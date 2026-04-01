package com.example.dto;

import java.time.LocalDateTime;

/**
 * SSE 실시간 스트리밍용 로봇 로그 이벤트 DTO
 *
 * robot_logs 테이블의 주요 필드를 담아 클라이언트에 전송
 */
public record RobotLogEvent(
        Long id,
        String robotId,
        double cpuUsage,
        long memUsed,
        long memTotal,
        int procsRunning,
        int procsBlocked,
        int priority,
        double posX,
        double posY,
        double posZ,
        double velLinearX,
        double velLinearY,
        double velAngularZ,
        String rosFrameId,
        String rosTopic,
        LocalDateTime recordedAt
) {
    /** RobotLog 엔티티로부터 변환 */
    public static RobotLogEvent from(com.example.entity.RobotLog log) {
        return new RobotLogEvent(
                log.getId(),
                log.getRobotId(),
                log.getCpuUsage(),
                log.getMemUsed(),
                log.getMemTotal(),
                log.getProcsRunning(),
                log.getProcsBlocked(),
                log.getPriority(),
                log.getPosX(),
                log.getPosY(),
                log.getPosZ(),
                log.getVelLinearX(),
                log.getVelLinearY(),
                log.getVelAngularZ(),
                log.getRosFrameId(),
                log.getRosTopic(),
                log.getRecordedAt()
        );
    }
}
