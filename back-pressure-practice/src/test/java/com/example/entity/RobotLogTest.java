package com.example.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RobotLogTest {

    @Test
    @DisplayName("RobotLog - Builder와 Getter/Setter가 정상 동작해야 한다")
    void robotLogBuilderAndGetterTest() {
        LocalDateTime now = LocalDateTime.now();
        RobotLog log = RobotLog.builder()
                .id(1L)
                .robotId("robot-1")
                .cpuUsage(75.5)
                .memUsed(4096L)
                .memTotal(8192L)
                .priority(2)
                .recordedAt(now)
                .build();

        assertThat(log.getId()).isEqualTo(1L);
        assertThat(log.getRobotId()).isEqualTo("robot-1");
        assertThat(log.getCpuUsage()).isEqualTo(75.5);
        assertThat(log.getMemUsed()).isEqualTo(4096L);
        assertThat(log.getMemTotal()).isEqualTo(8192L);
        assertThat(log.getPriority()).isEqualTo(2);
        assertThat(log.getRecordedAt()).isEqualTo(now);
    }
}
