package com.example.service;

import com.example.entity.RobotLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 우선순위 분리큐 버퍼
 *
 * 구조:
 *   - P1~P5 각각 독립된 ArrayBlockingQueue (분리큐)
 *   - overflow: 주 큐 포화 시 버퍼바운싱용 임시 큐
 *
 * 소비 순서: Q1 → Q2(aged 포함) → overflow → Q3 → Q4 → Q5
 *
 * Aging:
 *   - Q3/Q4/Q5 항목이 AGING_THRESHOLD 초과 시 Q2로 승격
 *   - 최대 승격 레벨 P2 고정 (P1 슬롯 보호)
 *
 * 버퍼바운싱:
 *   - 주 큐 포화 → overflow 큐 시도
 *   - overflow도 포화 → 최종 DROP
 */
@Component
public class RobotPriorityQueueBuffer {

    private static final Logger log = LoggerFactory.getLogger(RobotPriorityQueueBuffer.class);

    /** P3/P4/P5 항목이 이 시간 이상 대기 시 P2로 승격 */
    private static final Duration AGING_THRESHOLD = Duration.ofSeconds(10);

    public record PrioritizedEntry(RobotLog log, int effectivePriority, Instant enqueuedAt) {}

    // ── 분리큐: 우선순위별 독립 큐 ─────────────────────────────────────────────
    private final ArrayBlockingQueue<PrioritizedEntry> q1 = new ArrayBlockingQueue<>(256); // Critical
    private final ArrayBlockingQueue<PrioritizedEntry> q2 = new ArrayBlockingQueue<>(128); // High (+ aged)
    private final ArrayBlockingQueue<PrioritizedEntry> q3 = new ArrayBlockingQueue<>(64);  // Normal
    private final ArrayBlockingQueue<PrioritizedEntry> q4 = new ArrayBlockingQueue<>(64);  // Low
    private final ArrayBlockingQueue<PrioritizedEntry> q5 = new ArrayBlockingQueue<>(32);  // Idle

    /** 버퍼바운싱: 주 큐 포화 시 임시 overflow */
    private final ArrayBlockingQueue<PrioritizedEntry> overflow = new ArrayBlockingQueue<>(64);

    private final Map<Integer, ArrayBlockingQueue<PrioritizedEntry>> queueByPriority = Map.of(
            1, q1, 2, q2, 3, q3, 4, q4, 5, q5
    );

    private final AtomicLong dropCount  = new AtomicLong(0);
    private final AtomicLong agedCount  = new AtomicLong(0);

    // ── 삽입 ──────────────────────────────────────────────────────────────────

    /**
     * RobotLog를 해당 priority 큐에 삽입.
     * 주 큐 포화 시 overflow(버퍼바운싱) 시도, 그것도 포화면 최종 DROP.
     */
    public void enqueue(RobotLog robotLog) {
        PrioritizedEntry entry = new PrioritizedEntry(
                robotLog, robotLog.getPriority(), Instant.now());
        ArrayBlockingQueue<PrioritizedEntry> target = queueByPriority.get(robotLog.getPriority());

        if (!target.offer(entry)) {
            // 주 큐 포화 → 버퍼바운싱: overflow 큐로 재시도
            if (!overflow.offer(entry)) {
                // overflow도 포화 → 최종 DROP
                long total = dropCount.incrementAndGet();
                log.warn("[PQ] FINAL DROP priority={} robotId={} totalDropped={}",
                        robotLog.getPriority(), robotLog.getRobotId(), total);
            }
        }
    }

    // ── 소비 ──────────────────────────────────────────────────────────────────

    /**
     * 우선순위 순서로 소비하는 Flux 반환.
     * 50ms 간격으로 큐를 폴링하며, 한 사이클에 가용한 항목을 모두 방출한다.
     * 소비 순서: Q1 → Q2 → overflow → Q3 → Q4 → Q5
     */
    public Flux<RobotLog> consumeFlux() {
        return Flux.interval(Duration.ofMillis(50))
                .publishOn(Schedulers.boundedElastic())
                .flatMapIterable(tick -> {
                    List<RobotLog> batch = new ArrayList<>();
                    PrioritizedEntry entry;
                    while ((entry = pollInPriorityOrder()) != null) {
                        batch.add(entry.log());
                    }
                    return batch;
                });
    }

    // ── Aging ─────────────────────────────────────────────────────────────────

    /**
     * Q3/Q4/Q5 항목 중 AGING_THRESHOLD 초과된 것을 Q2로 승격.
     * Q2가 포화 상태면 원래 큐에 복원.
     * 최대 승격 레벨: P2 (P1로는 절대 승격하지 않음 → P1 슬롯 보호).
     */
    public void runAging() {
        Instant threshold = Instant.now().minus(AGING_THRESHOLD);

        for (ArrayBlockingQueue<PrioritizedEntry> sourceQ : List.of(q3, q4, q5)) {
            int snapshot = sourceQ.size();
            for (int i = 0; i < snapshot; i++) {
                PrioritizedEntry entry = sourceQ.poll();
                if (entry == null) break;

                if (entry.enqueuedAt().isBefore(threshold)) {
                    // AGING_THRESHOLD 초과 → P2로 승격 시도
                    PrioritizedEntry aged = new PrioritizedEntry(entry.log(), 2, entry.enqueuedAt());
                    if (q2.offer(aged)) {
                        long total = agedCount.incrementAndGet();
                        log.debug("[PQ] AGED P{} → P2 robotId={} totalAged={}",
                                entry.effectivePriority(), entry.log().getRobotId(), total);
                    } else {
                        // Q2 포화 → 원래 큐에 복원 (다음 aging 사이클에 재시도)
                        sourceQ.offer(entry);
                    }
                } else {
                    // 아직 threshold 미만 → 원래 큐에 복원
                    sourceQ.offer(entry);
                }
            }
        }
    }

    // ── 내부 폴링 ─────────────────────────────────────────────────────────────

    private PrioritizedEntry pollInPriorityOrder() {
        PrioritizedEntry entry;
        if ((entry = q1.poll()) != null) return entry;
        if ((entry = q2.poll()) != null) return entry;
        if ((entry = overflow.poll()) != null) return entry;
        if ((entry = q3.poll()) != null) return entry;
        if ((entry = q4.poll()) != null) return entry;
        if ((entry = q5.poll()) != null) return entry;
        return null;
    }

    public long getDropCount() { return dropCount.get(); }
    public long getAgedCount()  { return agedCount.get(); }
}
