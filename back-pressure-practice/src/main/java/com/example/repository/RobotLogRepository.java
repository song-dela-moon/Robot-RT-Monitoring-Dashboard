package com.example.repository;

import com.example.entity.RobotLog;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RobotLogRepository extends ReactiveCrudRepository<RobotLog, Long> {

    /**
     * 특정 로봇의 기간별 로그 조회 (시간 오름차순)
     */
    @Query("SELECT * FROM robot_logs WHERE robot_id = :robotId AND recorded_at BETWEEN :from AND :to ORDER BY recorded_at ASC")
    Flux<RobotLog> findByRobotIdAndRecordedAtBetween(String robotId, LocalDateTime from, LocalDateTime to);

    /**
     * DB에 이미 해당 로봇의 과거 데이터가 존재하는지 확인 (bulk insert 중복 방지)
     */
    @Query("SELECT COUNT(*) FROM robot_logs WHERE robot_id = :robotId AND recorded_at < :cutoff")
    Mono<Long> countHistoricalByRobotId(String robotId, LocalDateTime cutoff);

    /**
     * 존재하는 고유 robot_id 목록 조회
     */
    @Query("SELECT DISTINCT robot_id FROM robot_logs ORDER BY robot_id")
    Flux<String> findDistinctRobotIds();
}
