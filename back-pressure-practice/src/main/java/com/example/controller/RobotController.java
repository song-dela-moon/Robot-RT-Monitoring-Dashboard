package com.example.controller;

import com.example.dto.RobotLogEvent;
import com.example.entity.RobotLog;
import com.example.repository.RobotLogRepository;
import com.example.service.RobotSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 로봇 모니터링 REST + SSE 컨트롤러
 *
 * GET /api/robots                          → 등록된 로봇 ID 목록 (JSON)
 * GET /api/robots/{robotId}/logs           → 기간별 과거 로그 (JSON, weekly/monthly 용)
 * GET /api/robots/{robotId}/stream         → 실시간 SSE 스트리밍 (daily 전용)
 */
@RestController
@RequestMapping("/api/robots")
public class RobotController {

    private static final Logger log = LoggerFactory.getLogger(RobotController.class);

    private final RobotLogRepository repository;
    private final RobotSseService sseService;

    public RobotController(RobotLogRepository repository, RobotSseService sseService) {
        this.repository = repository;
        this.sseService = sseService;
    }

    // ── 로봇 목록 ─────────────────────────────────────────────────────────────

    /**
     * DB에 존재하는 고유 robot_id 목록 반환
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<String>> listRobots() {
        return repository.findDistinctRobotIds().collectList();
    }

    // ── 과거 로그 (REST) ──────────────────────────────────────────────────────

    /**
     * 기간별 과거 로그 조회
     *
     * @param robotId 로봇 ID
     * @param from    시작 시각 (ISO: 2026-03-25T00:00:00)
     * @param to      종료 시각 (ISO: 2026-04-01T23:59:59)
     *
     * daily: from = 오늘 시작, to = 현재
     * weekly: from = 7일 전, to = 지금
     * monthly: from = 30일 전, to = 지금
     */
    @GetMapping(value = "/{robotId}/logs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<RobotLog> getLogs(
            @PathVariable String robotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("[ROBOT] getLogs robotId={} from={} to={}", robotId, from, to);
        return repository.findByRobotIdAndRecordedAtBetween(robotId, from, to);
    }

    // ── 실시간 SSE (daily 전용) ───────────────────────────────────────────────

    /**
     * 실시간 SSE 스트리밍 — daily 모드에서 사용
     *
     * Zone 1 Back Pressure: RobotSseService 내부 onBackpressureBuffer(256, DROP_OLDEST)
     *
     * 클라이언트 연결 시 RobotSseService 의 Hot Flux 를 구독하므로
     * 연결 이전의 이벤트는 수신되지 않는다.
     *
     * @param robotId     로봇 ID
     * @param minPriority 최소 priority 필터 (1~5, 기본값 3: priority 1~3만 SSE 전송)
     */
    @GetMapping(value = "/{robotId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RobotLogEvent>> streamRobotLogs(
            @PathVariable String robotId,
            @RequestParam(defaultValue = "3") int minPriority) {

        log.info("[ROBOT-SSE] New subscriber robotId={} minPriority={}", robotId, minPriority);

        return sseService.streamFor(robotId)
                // priority 기준 필터링 (낮은 번호 = 높은 우선순위)
                .filter(event -> event.priority() <= minPriority)
                .map(event -> ServerSentEvent.<RobotLogEvent>builder()
                        .id(String.valueOf(event.id()))
                        .event("robot-log")
                        .data(event)
                        .build())
                .doOnCancel(() -> log.info("[ROBOT-SSE] Client disconnected robotId={}", robotId));
    }
}
