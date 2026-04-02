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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 로봇 더미 데이터 생성 서비스
 *
 * 1) @PostConstruct: 30일치 과거 데이터 Bulk Insert (없는 경우에만)
 *    robot-001 ~ robot-005, 1분 간격, 약 21만 6천 건
 *
 * 2) 실시간 생성: 우선순위 분리큐 + 시나리오 기반 테스트
 *    ScenarioPhase 에 따라 생성 패턴이 변경됨
 *
 * ── 테스트 시나리오 타임라인 ──────────────────────────────────────────────
 *   0s  ~ 30s  : NORMAL     — 5개/초, 가우시안 랜덤 (기준선)
 *   30s ~ 60s  : P1_BURST   — 5개/초, 전부 CPU≥85% 강제
 *                             → Q1 포화 → overflow 버퍼바운싱 → FINAL DROP 관찰
 *   60s ~ 90s  : FLOOD      — 50개/초 (로봇당 10배), 랜덤 우선순위
 *                             → 모든 큐 포화 → FINAL DROP 집중 관찰
 *   90s ~ 150s : STARVATION — 5개/초, P1/P2(CPU≥65%)만 생성
 *                             → Q3/Q4/Q5 기아 → 10초 후 aging 발동 확인
 *   150s~       : NORMAL     — 복귀
 */
@Service
public class RobotDummyDataService {

    /**
     * 실시간 생성 시나리오 단계
     *
     * NORMAL     : 가우시안 랜덤 CPU → 자연스러운 우선순위 분포
     * P1_BURST   : CPU 85~100% 고정 → Q1 집중 포화, 버퍼바운싱/DROP 유발
     * FLOOD      : 로봇당 10배 생성 → 모든 큐 동시 포화, FINAL DROP 집중 유발
     * STARVATION : CPU 65~100% 고정 (P1/P2만) → Q3/Q4/Q5 기아, aging 발동 확인
     */
    private enum ScenarioPhase {
        NORMAL, P1_BURST, FLOOD, STARVATION
    }

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
    private final RobotPriorityQueueBuffer priorityQueue;
    private final Random random = new Random();

    /** 현재 실행 중인 시나리오 단계 */
    private final AtomicReference<ScenarioPhase> currentPhase =
            new AtomicReference<>(ScenarioPhase.NORMAL);

    public RobotDummyDataService(RobotLogRepository repository,
                                  RobotSseService sseService,
                                  RobotPriorityQueueBuffer priorityQueue) {
        this.repository     = repository;
        this.sseService     = sseService;
        this.priorityQueue  = priorityQueue;
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

    // ── 실시간 데이터 생성 (우선순위 분리큐 + Aging + 시나리오) ──────────────

    private void startRealTimeGenerator() {
        log.info("[DUMMY] Starting real-time generator with scenario support");

        startScenarioScheduler();
        startAgingScheduler();
        startProducer();
        startConsumer();
    }

    /**
     * 시나리오 전환 스케줄러
     * 앱 시작 기준 고정 타임라인으로 currentPhase를 전환한다.
     */
    private void startScenarioScheduler() {
        // 전환 시점(초) → 다음 단계 매핑
        List<Map.Entry<Long, ScenarioPhase>> timeline = List.of(
                Map.entry(30L,  ScenarioPhase.P1_BURST),
                Map.entry(60L,  ScenarioPhase.FLOOD),
                Map.entry(90L,  ScenarioPhase.STARVATION),
                Map.entry(150L, ScenarioPhase.NORMAL)
        );

        for (Map.Entry<Long, ScenarioPhase> step : timeline) {
            Mono.delay(Duration.ofSeconds(step.getKey()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(t -> {
                        ScenarioPhase next = step.getValue();
                        currentPhase.set(next);
                        log.info("[SCENARIO] ===== Phase → {} =====", next);
                        logScenarioDescription(next);
                    });
        }
    }

    private void logScenarioDescription(ScenarioPhase phase) {
        switch (phase) {
            case P1_BURST ->
                log.info("[SCENARIO] P1_BURST: CPU 85~100% 강제, 5개/초 → Q1 포화, 버퍼바운싱/DROP 관찰");
            case FLOOD ->
                log.info("[SCENARIO] FLOOD: 50개/초 (로봇당 10배) → 모든 큐 포화, FINAL DROP 집중 관찰");
            case STARVATION ->
                log.info("[SCENARIO] STARVATION: P1/P2만 생성, Q3/Q4/Q5 기아 → 10초 후 aging 발동 확인");
            case NORMAL ->
                log.info("[SCENARIO] NORMAL: 가우시안 랜덤 5개/초 복귀");
        }
    }

    /**
     * Aging 스케줄러: 1초마다 Q3/Q4/Q5 항목의 대기 시간 체크
     * 임계값 초과 시 최대 P2로 승격 (P1 슬롯 보호)
     */
    private void startAgingScheduler() {
        Flux.interval(Duration.ofSeconds(1))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        tick -> priorityQueue.runAging(),
                        err  -> log.error("[PQ] Aging scheduler error", err));
    }

    /**
     * Producer: 1초마다 현재 시나리오에 맞는 로그를 생성하여 우선순위 큐에 삽입
     *
     * NORMAL     : 5개/초 (로봇당 1개), 랜덤 CPU
     * P1_BURST   : 5개/초, CPU 85~100% 고정
     * FLOOD      : 50개/초 (로봇당 10개), 랜덤 CPU → 버퍼 초과 유도
     * STARVATION : 5개/초, CPU 65~100% 고정 (P1/P2만)
     */
    private void startProducer() {
        Flux.interval(Duration.ofSeconds(1))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tick -> generateForCurrentPhase())
                .subscribe(
                        priorityQueue::enqueue,
                        err -> log.error("[PQ] Producer error", err));
    }

    private Flux<RobotLog> generateForCurrentPhase() {
        ScenarioPhase phase = currentPhase.get();
        LocalDateTime now = LocalDateTime.now();

        // FLOOD: 로봇당 10개씩 생성하여 큐 포화 유도
        int perRobot = (phase == ScenarioPhase.FLOOD) ? 10 : 1;

        List<RobotLog> batch = new ArrayList<>(ROBOT_IDS.size() * perRobot);
        for (String robotId : ROBOT_IDS) {
            for (int i = 0; i < perRobot; i++) {
                batch.add(buildRobotLogForScenario(robotId, now, phase));
            }
        }
        return Flux.fromIterable(batch);
    }

    /**
     * Consumer: 우선순위 큐에서 꺼내 DB 저장 후 SSE 전달
     * 소비 순서: Q1 → Q2(aged 포함) → overflow → Q3 → Q4 → Q5
     */
    private void startConsumer() {
        priorityQueue.consumeFlux()
                .flatMap(logEntry -> repository.save(logEntry)
                        .doOnError(err -> log.error("[DB-BP] Save error: {}", err.getMessage()))
                        .onErrorResume(err -> Mono.<RobotLog>empty()), 4)
                .map(RobotLogEvent::from)
                .doOnNext(sseService::pushEvent)
                .subscribe(
                        event -> log.debug("[DUMMY] saved+pushed robotId={} priority={}",
                                event.robotId(), event.priority()),
                        err   -> log.error("[DUMMY] Consumer error", err));
    }

    // ── 더미 데이터 생성 로직 ─────────────────────────────────────────────────

    /**
     * 시나리오별 CPU 범위로 로그 생성
     *
     * NORMAL     : 가우시안(평균 45%, σ 20%) → 자연스러운 P1~P5 분포
     * P1_BURST   : 85~100% 고정 → 항상 P1
     * FLOOD      : NORMAL 동일 (대신 volume으로 포화)
     * STARVATION : 65~100% 고정 → P1(≥85%) 또는 P2(65~85%)만 생성
     */
    private RobotLog buildRobotLogForScenario(String robotId, LocalDateTime timestamp, ScenarioPhase phase) {
        double cpu = switch (phase) {
            case P1_BURST   -> 85.0 + random.nextDouble() * 15.0;          // 85~100% → 항상 P1
            case STARVATION -> 65.0 + random.nextDouble() * 35.0;          // 65~100% → P1 또는 P2
            default         -> Math.min(100.0, Math.max(0.0,               // 가우시안 랜덤
                                        random.nextGaussian() * 20 + 45));
        };
        return buildRobotLogWithCpu(robotId, timestamp, cpu);
    }

    /**
     * 과거 데이터 Bulk Insert 용 빌더 (가우시안 랜덤)
     */
    private RobotLog buildRobotLog(String robotId, LocalDateTime timestamp) {
        double cpu = Math.min(100.0, Math.max(0.0, random.nextGaussian() * 20 + 45));
        return buildRobotLogWithCpu(robotId, timestamp, cpu);
    }

    /**
     * 실제 RobotLog 조립 — CPU 값은 외부에서 결정하여 주입
     */
    private RobotLog buildRobotLogWithCpu(String robotId, LocalDateTime timestamp, double cpu) {
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
