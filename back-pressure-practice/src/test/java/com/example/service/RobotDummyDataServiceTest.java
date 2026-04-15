package com.example.service;

import com.example.entity.RobotLog;
import com.example.repository.RobotLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RobotDummyDataServiceTest {

    @Mock
    private RobotLogRepository repository;
    @Mock
    private RobotPriorityQueueBuffer priorityQueue;
    @Mock
    private RobotSseService sseService;

    private RobotDummyDataService service;

    @BeforeEach
    void setUp() {
        service = new RobotDummyDataService(repository, priorityQueue, sseService);
    }

    @Test
    @DisplayName("getCurrentPhase - 시간에 따라 시나리오가 올바르게 순환되어야 한다")
    void getCurrentPhaseTest() {
        // 0~19: NORMAL, 20~39: P1_BURST, 40~59: FLOOD, 60~79: STARVATION
        assertThat(service.getCurrentPhase(0)).isEqualTo(RobotDummyDataService.ScenarioPhase.NORMAL);
        assertThat(service.getCurrentPhase(20)).isEqualTo(RobotDummyDataService.ScenarioPhase.P1_BURST);
        assertThat(service.getCurrentPhase(40)).isEqualTo(RobotDummyDataService.ScenarioPhase.FLOOD);
        assertThat(service.getCurrentPhase(60)).isEqualTo(RobotDummyDataService.ScenarioPhase.STARVATION);
        
        // 주기 순환 확인 (80초 후 다시 NORMAL)
        assertThat(service.getCurrentPhase(80)).isEqualTo(RobotDummyDataService.ScenarioPhase.NORMAL);
    }

    @Test
    @DisplayName("generateBatch - NORMAL 시나리오에서는 각 로봇당 1개씩 생성해야 한다")
    void generateBatchNormalTest() {
        StepVerifier.create(service.generateBatch(RobotDummyDataService.ScenarioPhase.NORMAL))
                .expectNextCount(5) // 로봇 ID가 5개이므로
                .verifyComplete();
    }

    @Test
    @DisplayName("generateBatch - FLOOD 시나리오에서는 각 로봇당 10개씩 생성해야 한다")
    void generateBatchFloodTest() {
        StepVerifier.create(service.generateBatch(RobotDummyDataService.ScenarioPhase.FLOOD))
                .expectNextCount(50) // 5 로봇 * 10개씩
                .verifyComplete();
    }

    @Test
    @DisplayName("buildRobotLogForScenario - 각 시나리오별 우선순위 분포를 확인 (커버리지용)")
    void scenarioLogGenerationTest() {
        // P1_BURST는 CPU가 높아야 함
        RobotLog p1Log = service.generateBatch(RobotDummyDataService.ScenarioPhase.P1_BURST).blockFirst();
        assertThat(p1Log.getCpuUsage()).isGreaterThanOrEqualTo(85.0);
        assertThat(p1Log.getPriority()).isEqualTo(1);

        // STARVATION은 CPU가 65 이상이어야 함
        RobotLog starvationLog = service.generateBatch(RobotDummyDataService.ScenarioPhase.STARVATION).blockFirst();
        assertThat(starvationLog.getCpuUsage()).isGreaterThanOrEqualTo(65.0);
        assertThat(starvationLog.getPriority()).isLessThanOrEqualTo(2);
    }
}
