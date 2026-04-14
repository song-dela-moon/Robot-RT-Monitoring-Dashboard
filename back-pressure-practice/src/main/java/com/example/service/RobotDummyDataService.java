package com.example.service;

import com.example.dto.RobotLogEvent;
import com.example.entity.RobotLog;
import com.example.repository.RobotLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 로봇 더미 데이터 생성 서비스 (수정본)
 *
 * 1) 로봇 ID: robot-1, robot-2, robot-3, robot-4, robot-5
 * 2) 로직: 1초 간격 데이터 생성 -> 우선순위 큐(Back Pressure) -> DB 저장 -> SSE 전송
 * 3) 시나리오: NORMAL -> P1_BURST -> FLOOD -> STARVATION 순환
 */
@Service
public class RobotDummyDataService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RobotDummyDataService.class);

    private static final List<String> ROBOT_IDS = List.of(
            "robot-1", "robot-2", "robot-3", "robot-4", "robot-5"
    );

    private static final long MEM_TOTAL_KB = 8192 * 1024; // 8GB

    private final RobotLogRepository repository;
    private final RobotPriorityQueueBuffer priorityQueue;
    private final RobotSseService sseService;
    private final Random random = new Random();

    public RobotDummyDataService(RobotLogRepository repository,
                                 RobotPriorityQueueBuffer priorityQueue,
                                 RobotSseService sseService) {
        this.repository = repository;
        this.priorityQueue = priorityQueue;
        this.sseService = sseService;
    }

    private enum ScenarioPhase {
        NORMAL,      // 평시 (균등 분포)
        P1_BURST,    // P1 급증 (CPU > 85%)
        FLOOD,       // 데이터 대량 발생 (초당 10개씩)
        STARVATION   // P1/P2 위주 생성 (하위 우선순위 기아 상태 유도)
    }

    @Override
    public void run(String... args) {
        log.info("[DUMMY] Initializing RobotDummyDataService with robots: {}", ROBOT_IDS);
        startProducer();
        startConsumer();
        startAgingTask();
    }

    /**
     * Producer: 1초 마다 시나리오에 맞는 데이터 생성하여 큐에 삽입
     */
    private void startProducer() {
        Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> {
                    ScenarioPhase phase = getCurrentPhase(tick);
                    return generateBatch(phase);
                })
                .subscribe(priorityQueue::enqueue);
    }

    private ScenarioPhase getCurrentPhase(long tick) {
        // 20초마다 시나리오 전환 (80초 주기)
        int idx = (int) ((tick / 20) % ScenarioPhase.values().length);
        ScenarioPhase phase = ScenarioPhase.values()[idx];
        if (tick % 20 == 0) {
            log.info("<<<< [SCENARIO CHANGE] Current Phase: {} >>>>", phase);
        }
        return phase;
    }

    private Flux<RobotLog> generateBatch(ScenarioPhase phase) {
        LocalDateTime now = LocalDateTime.now();
        // FLOOD 시나리오에서는 로봇당 10개씩 생성하여 큐 포화 유도
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
     */
    private void startConsumer() {
        priorityQueue.consumeFlux()
                .flatMap(logEntry -> repository.save(logEntry)
                        .doOnError(err -> log.error("[DB-BP] Save error: {}", err.getMessage()))
                        .onErrorResume(err -> Mono.empty()), 4) // 병렬 저장 4개까지
                .map(RobotLogEvent::from)
                .doOnNext(sseService::pushEvent)
                .subscribe(
                        event -> log.debug("[DUMMY] saved+pushed robotId={} priority={}",
                                event.robotId(), event.priority()),
                        err   -> log.error("[DUMMY] Consumer error", err));
    }

    private void startAgingTask() {
        Flux.interval(Duration.ofSeconds(5))
                .subscribe(tick -> priorityQueue.runAging());
    }

    // ── 데이터 생성 로직 ──────────────────────────────────────────────────

    private RobotLog buildRobotLogForScenario(String robotId, LocalDateTime timestamp, ScenarioPhase phase) {
        double cpu = switch (phase) {
            case P1_BURST   -> 85.0 + random.nextDouble() * 15.0;          // 항상 P1
            case STARVATION -> 65.0 + random.nextDouble() * 35.0;          // P1 또는 P2
            default         -> Math.min(100.0, Math.max(0.0, random.nextGaussian() * 20 + 45));
        };

        // 우선순위 결정 (CPU 기반)
        int priority;
        if (cpu >= 85) priority = 1;
        else if (cpu >= 65) priority = 2;
        else if (cpu >= 40) priority = 3;
        else if (cpu >= 20) priority = 4;
        else priority = 5;

        // 메모리 및 기타 수치
        long memUsed = (long) (MEM_TOTAL_KB * (0.3 + random.nextDouble() * 0.5));
        int procsRunning = (int) Math.max(0, random.nextGaussian() * 2 + 3);
        int procsBlocked = random.nextInt(5);

        // 오도메트리
        double seed = timestamp.toEpochSecond(ZoneOffset.UTC);
        double posX = Math.sin(seed / 60.0) * 10.0 + random.nextGaussian() * 0.1;
        double posY = Math.cos(seed / 60.0) * 10.0 + random.nextGaussian() * 0.1;

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
                .posZ(0.0)
                .velLinearX(Math.round(random.nextGaussian() * 1000.0) / 1000.0)
                .velLinearY(0.0)
                .velAngularZ(0.0)
                .rosFrameId("odom")
                .rosTopic("/" + robotId.replace("-", "") + "/odom")
                .recordedAt(timestamp)
                .build();
    }
}
