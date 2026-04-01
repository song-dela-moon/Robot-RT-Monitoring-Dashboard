package com.example.service;

import com.example.dto.RobotLogEvent;
import com.example.entity.RobotLog;
import com.example.repository.RobotLogRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 로봇 SSE 서비스
 *
 * Back Pressure Zone 1: DB 저장 완료 → SSE Sink emit (onBackpressureBuffer)
 *
 * 각 robot_id 별로 독립적인 Sinks.Many 를 관리한다.
 * DB 저장 완료 후 RobotDummyDataService 가 pushEvent() 를 호출해 이벤트를 emit 한다.
 * 클라이언트가 느리면 최대 256개를 버퍼링하고 초과 시 가장 오래된 이벤트를 DROP 한다.
 */
@Service
public class RobotSseService {

    private static final Logger log = LoggerFactory.getLogger(RobotSseService.class);

    /** robot_id → Hot Publisher Sink */
    private final Map<String, Sinks.Many<RobotLogEvent>> sinks = new ConcurrentHashMap<>();

    private final AtomicLong sseDropCount = new AtomicLong(0);

    /**
     * 특정 로봇의 SSE Flux 반환
     * 구독 해제 시 자동으로 정리되지 않으며, sink 는 앱 생명주기 동안 유지된다.
     */
    public Flux<RobotLogEvent> streamFor(String robotId) {
        Sinks.Many<RobotLogEvent> sink = getOrCreateSink(robotId);
        // Zone 1 Back Pressure: 구독자가 느리면 onBackpressureBuffer 256개 → 초과 시 DROP_OLDEST
        return sink.asFlux()
                .onBackpressureBuffer(256, dropped -> {
                    long count = sseDropCount.incrementAndGet();
                    log.warn("[SSE-BP] Zone1 DROP_OLDEST robotId={} totalDropped={}", robotId, count);
                });
    }

    /**
     * DB 저장 완료 후 호출 → SSE 구독자에게 이벤트 전달
     *
     * @param event 저장된 로봇 로그 이벤트
     */
    public void pushEvent(RobotLogEvent event) {
        Sinks.EmitResult result = getOrCreateSink(event.robotId()).tryEmitNext(event);
        if (result.isFailure()) {
            log.debug("[SSE-BP] emit failed robotId={} result={}", event.robotId(), result);
        }
    }

    private Sinks.Many<RobotLogEvent> getOrCreateSink(String robotId) {
        return sinks.computeIfAbsent(robotId, id ->
                Sinks.many().multicast().onBackpressureBuffer(256, false));
    }
}
