package com.example;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebfluxReactiveStreamsTestApplicationTests {

	@Disabled(" Jenkins 환경에서 DB 연결 없이 컨텍스트 로딩이 실패하는 것을 방지하기 위해 임시 비활성화")
	@Test
	void contextLoads() {
	}

}
