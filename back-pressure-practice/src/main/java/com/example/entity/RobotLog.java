package com.example.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * robot_logs 테이블 매핑 엔티티
 *
 * 필드 그룹:
 * 1) CPU / vmstat: cpu_usage, mem_used, mem_total, procs_running, procs_blocked
 * 2) 우선순위: priority (1=highest ~ 5=lowest)
 * 3) 오도메트리(ROS nav_msgs/Odometry): pos_x/y/z, vel_linear_x/y, vel_angular_z
 * 4) ROS 메타: ros_frame_id, ros_topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("robot_logs")
public class RobotLog {

    @Id
    private Long id;

    @Column("robot_id")
    private String robotId;

    /** CPU 사용률 (%) */
    @Column("cpu_usage")
    private double cpuUsage;

    /** 사용 중인 메모리 (KB) — vmstat swpd / free 대신 실사용량 */
    @Column("mem_used")
    private long memUsed;

    /** 전체 메모리 (KB) */
    @Column("mem_total")
    private long memTotal;

    /** 실행 중 프로세스 수 — vmstat r */
    @Column("procs_running")
    private int procsRunning;

    /** 블로킹된 프로세스 수 — vmstat b */
    @Column("procs_blocked")
    private int procsBlocked;

    /** 이벤트 우선순위 1(긴급) ~ 5(정보) */
    @Column("priority")
    private int priority;

    // ── Odometry (nav_msgs/Odometry) ──────────────────────────────────────────

    /** pose.position.x (m) */
    @Column("pos_x")
    private double posX;

    /** pose.position.y (m) */
    @Column("pos_y")
    private double posY;

    /** pose.position.z (m) */
    @Column("pos_z")
    private double posZ;

    /** twist.linear.x (m/s) */
    @Column("vel_linear_x")
    private double velLinearX;

    /** twist.linear.y (m/s) */
    @Column("vel_linear_y")
    private double velLinearY;

    /** twist.angular.z (rad/s) */
    @Column("vel_angular_z")
    private double velAngularZ;

    // ── ROS 메타데이터 ───────────────────────────────────────────────────────

    /** header.frame_id (예: "odom") */
    @Column("ros_frame_id")
    private String rosFrameId;

    /** 발행 토픽 (예: "/robot001/odom") */
    @Column("ros_topic")
    private String rosTopic;

    /** 수신/기록 시각 */
    @Column("recorded_at")
    private LocalDateTime recordedAt;
}
