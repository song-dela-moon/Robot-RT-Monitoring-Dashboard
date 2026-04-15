package com.example.controller;

import com.example.entity.RobotLog;
import com.example.repository.RobotLogRepository;
import com.example.service.RobotSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * RobotController 단위 테스트 (WebTestClient + Mockito)
 * Spring Context를 띄우지 않아 매우 빠르며 의존성 문제가 적음
 */
@ExtendWith(MockitoExtension.class)
class RobotControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private RobotLogRepository repository;

    @Mock
    private RobotSseService sseService;

    @InjectMocks
    private RobotController robotController;

    @BeforeEach
    void setUp() {
        // 컨트롤러에 Mock 객체들을 주입하여 WebTestClient 생성
        webTestClient = WebTestClient.bindToController(robotController).build();
    }

    @Test
    @DisplayName("GET /api/robots - 로봇 ID 목록을 정상적으로 반환해야 한다")
    void listRobotsTest() {
        // given
        Mockito.when(repository.findDistinctRobotIds())
               .thenReturn(Flux.fromIterable(Arrays.asList("robot-1", "robot-2")));

        // when & then
        webTestClient.get()
                .uri("/api/robots")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0]").isEqualTo("robot-1")
                .jsonPath("$[1]").isEqualTo("robot-2");
    }

    @Test
    @DisplayName("GET /api/robots/{id}/logs - 특정 로봇의 로그를 기간별로 조회해야 한다")
    void getLogsTest() {
        // given
        RobotLog log1 = RobotLog.builder()
                .id(1L)
                .robotId("robot-1")
                .cpuUsage(50.0)
                .priority(1)
                .recordedAt(LocalDateTime.now())
                .build();

        Mockito.when(repository.findByRobotIdAndRecordedAtBetween(anyString(), any(), any()))
               .thenReturn(Flux.just(log1));

        // when & then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/robots/robot-1/logs")
                        .queryParam("from", "2026-04-15T00:00:00")
                        .queryParam("to", "2026-04-15T23:59:59")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].robotId").isEqualTo("robot-1");
    }
}
