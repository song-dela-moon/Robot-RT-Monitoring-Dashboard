package com.example.service;

import com.example.dto.RobotLogEvent;
import com.example.entity.RobotLog;
import com.example.repository.RobotLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 로봇 더미 데이터 생성 서비스
 *
 * 1) @PostConstruct: 30일치 과거 데이터 Bulk Insert (없는 경우에만)
 *    robot-001 ~ robot-005, 1분 간격, 약 21만 6천 건
 *
 * 2) Flux.interval(1s): 실시간 더미 데이터 생성
 *    Back Pressure Zone 2: onBackpressureBuffer(512, DROP) → DB flatMap save
 *    → 저장 완료 후 RobotSseService.pushEvent() 호출 (Zone 1 연계)
 */
@Service
public class RobotDummyDataService {

    private static final Logger log = LoggerFactory.getLogger(RobotDummyDataService.class);

    private static final List<String> ROBOT_IDS = List.of(
            "robot-001", "robot-002", "robot-003", "robot-004", "robot-005");

    /** 과거 데이터 생성 일수 */
    private static final int HISTORY_DAYS = 30;

    /** 과거 데이터 삽입 간격 (분) */
    private static final int HISTORY_INTERVAL_MINUTES = 1;

    private static final long MEM_TOTAL_KB = 16L * 1024 * 1024; // 16 GB

    private final RobotLogRepository repository;
    private final RobotSseService sseService;
    private final Random random = new Random();

    private final AtomicLong dbDropCount = new AtomicLong(0);

    public RobotDummyDataService(RobotLogRepository repository, RobotSseService sseService) {
        this.repository = repository;
        this.sseService = sseService;
    }

    /**
     * 앱 시작 시:
     * 1) 과거 30일 데이터 bulk insert (이미 있으면 skip)
     * 2) 실시간 1초 간격 생성 시작
     */
    @PostConstruct
    public void init() {
        insertHistoricalDataIfNeeded()
                .doOnTerminate(this::startRealTimeGenerator)
                .subscribe(
                        count -> log.info("[DUMMY] Historical insert done, total saved={}", count),
                        err  -> log.error("[DUMMY] Historical insert error", err)
                );
    }

    // ── 과거 데이터 Bulk Insert ────────────────────────────────────────────────

    /**
     * 각 로봇에 대해 DB에 과거 데이터가 없으면 bulk insert
     */
    private Flux<Long> insertHistoricalDataIfNeeded() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(1); // '오늘 것'은 실시간 생성이 담당

        return Flux.fromIterable(ROBOT_IDS)
                .flatMap(robotId ->
                        repository.countHistoricalByRobotId(robotId, cutoff)
                                .flatMapMany(count -> {
                                    if (count > 0) {
                                        log.info("[DUMMY] Skip historical insert: robotId={} existing={}", robotId, count);
                                        return Flux.just(count);
                                    }
                                    return insertHistoryForRobot(robotId, now);
                                })
                );
    }

    private Flux<Long> insertHistoryForRobot(String robotId, LocalDateTime now) {
        LocalDateTime start = now.minusDays(HISTORY_DAYS);
        long totalMinutes = (long) HISTORY_DAYS * 24 * 60;

        log.info("[DUMMY] Inserting {}d history for robotId={} ({} records)", HISTORY_DAYS, robotId, totalMinutes);

        return Flux.range(0, (int) totalMinutes)
                .map(i -> {
                    LocalDateTime ts = start.plusMinutes((long) i * HISTORY_INTERVAL_MINUTES);
                    return buildRobotLog(robotId, ts);
                })
                // Zone 2 Back Pressure: DB 저장 속도에 맞게 flatMap concurrency 제한
                .flatMap(logEntry -> repository.save(logEntry), 8)
                .map(saved -> 1L)
                .scan(0L, Long::sum)
                .doOnNext(total -> {
                    if (total % 5000 == 0 && total > 0) {
                        log.info("[DUMMY] History insert progress: robotId={} saved={}/{}", robotId, total, totalMinutes);
                    }
                });
    }

    // ── 실시간 데이터 생성 (Zone 2 Back Pressure) ─────────────────────────────

    /**
     * 1초마다 각 로봇의 더미 로그를 생성하여 DB에 저장
     * Back Pressure Zone 2: onBackpressureBuffer(512, DROP) → flatMap(concurrency=4)
     */
    private void startRealTimeGenerator() {
        log.info("[DUMMY] Starting real-time generator (1s interval)");

        Flux.interval(Duration.ofSeconds(1))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tick -> Flux.fromIterable(ROBOT_IDS)
                        .map(robotId -> buildRobotLog(robotId, LocalDateTime.now())))
                // Zone 2 Back Pressure: 512개 버퍼, 초과 시 DROP (실험용 더미 여서 손실 허용)
                .onBackpressureBuffer(512, dropped ->
                        log.warn("[DB-BP] Zone2 DROP robotId=? dropped={} totalDropped={}",
                                dropped, dbDropCount.incrementAndGet()))
                // DB 저장: 동시성 4 (너무 많으면 DB 커넥션 포화)
                .flatMap(logEntry -> repository.save(logEntry)
                        .doOnError(err -> log.error("[DB-BP] Save error: {}", err.getMessage()))
                        .onErrorResume(err -> Mono.<RobotLog>empty()), 4)
                // 저장 완료 → SSE Zone 1 연계
                .map(saved -> RobotLogEvent.from(saved))
                .doOnNext(sseService::pushEvent)
                .subscribe(
                        event -> log.debug("[DUMMY] RT saved+pushed robotId={} priority={}", event.robotId(), event.priority()),
                        err   -> log.error("[DUMMY] RT generator error", err)
                );
    }

    // ── 더미 데이터 생성 로직 ─────────────────────────────────────────────────

    /**
     * 로봇 로그 더미 데이터 빌더
     *
     * - CPU: 0~100%, 가우시안 분포로 더 현실적으로
     * - vmstat: 실행/블로킹 프로세스 수
     * - priority: 1~5 (1=CPU 80+, 2=CPU 60-80, 3=normal, 4=low, 5=idle)
     * - odom: 랜덤 워크 (로봇이 움직이는 느낌)
     * - ROS: frame_id="odom", topic="/robot001/odom" 형식
     */
    private RobotLog buildRobotLog(String robotId, LocalDateTime timestamp) {
        // CPU 사용률 (0~100)
        double cpu = Math.min(100.0, Math.max(0.0,
                random.nextGaussian() * 20 + 45));  // 평균 45%, 표준편차 20%

        // 메모리 사용
        long memUsed = (long) (MEM_TOTAL_KB * (0.3 + random.nextDouble() * 0.5));

        // vmstat r/b
        int procsRunning = (int) Math.max(0, random.nextGaussian() * 2 + 3);
        int procsBlocked = random.nextInt(5);

        // priority: CPU 기반 결정 (높을수록 긴급)
        int priority;
        if (cpu >= 85) priority = 1;
        else if (cpu >= 65) priority = 2;
        else if (cpu >= 40) priority = 3;
        else if (cpu >= 20) priority = 4;
        else priority = 5;

        // 오도메트리: 간단한 랜덤 워크
        double seed = timestamp.toEpochSecond(ZoneOffset.UTC);
        double posX = Math.sin(seed / 60.0) * 10.0 + random.nextGaussian() * 0.1;
        double posY = Math.cos(seed / 60.0) * 10.0 + random.nextGaussian() * 0.1;
        double posZ = 0.0;
        double velLinearX = Math.cos(seed / 60.0) * 0.5 + random.nextGaussian() * 0.05;
        double velLinearY = -Math.sin(seed / 60.0) * 0.5 + random.nextGaussian() * 0.05;
        double velAngularZ = (random.nextDouble() - 0.5) * 0.3;

        // ROS 토픽 이름 (robot-001 → /robot001/odom)
        String topicName = "/" + robotId.replace("-", "") + "/odom";

        return RobotLog.builder()
                .robotId(robotId)
                .cpuUsage(Math.round(cpu * 100.0) / 100.0)
                .memUsed(memUsed)
                .memTotal(MEM_TOTAL_KB)
                .procsRunning(procsRunning)
                .procsBlocked(procsBlocked)
                .priority(priority)
                .posX(Math.round(posX * 1000.0) / 1000.0)
                .posY(Math.round(posY * 1000.0) / 1000.0)
                .posZ(posZ)
                .velLinearX(Math.round(velLinearX * 10000.0) / 10000.0)
                .velLinearY(Math.round(velLinearY * 10000.0) / 10000.0)
                .velAngularZ(Math.round(velAngularZ * 10000.0) / 10000.0)
                .rosFrameId("odom")
                .rosTopic(topicName)
                .recordedAt(timestamp)
                .build();
    }
}
