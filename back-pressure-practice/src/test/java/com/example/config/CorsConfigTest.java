package com.example.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.reactive.CorsWebFilter;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    @DisplayName("CorsWebFilter 빈이 정상적으로 생성되어야 한다")
    void corsWebFilterBeanTest() {
        CorsConfig config = new CorsConfig();
        CorsWebFilter filter = config.corsWebFilter();
        
        assertThat(filter).isNotNull();
    }
}
