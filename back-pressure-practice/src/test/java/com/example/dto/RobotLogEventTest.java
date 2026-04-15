package com.example.dto;

import com.example.entity.RobotLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RobotLogEventTest {

    @Test
    @DisplayName("RobotLogEvent - from 정적 팩토리 메서드가 정상 동작해야 한다")
    void robotLogEventFromTest() {
        LocalDateTime now = LocalDateTime.now();
        RobotLog log = RobotLog.builder()
                .id(1L)
                .robotId("robot-1")
                .cpuUsage(75.5)
                .memUsed(4096L)
                .memTotal(8192L)
                .procsRunning(5)
                .procsBlocked(2)
                .priority(2)
                .posX(1.0)
                .posY(2.0)
                .posZ(0.0)
                .velLinearX(0.5)
                .velLinearY(0.0)
                .velAngularZ(0.1)
                .rosFrameId("map")
                .rosTopic("/odom")
                .recordedAt(now)
                .build();

        RobotLogEvent event = RobotLogEvent.from(log);

        assertThat(event.id()).isEqualTo(1L);
        assertThat(event.robotId()).isEqualTo("robot-1");
        assertThat(event.cpuUsage()).isEqualTo(75.5);
        assertThat(event.priority()).isEqualTo(2);
        assertThat(event.recordedAt()).isEqualTo(now);
    }
}
