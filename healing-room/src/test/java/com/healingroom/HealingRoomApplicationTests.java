package com.healingroom;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실제 DB·Redis 에 접속해 앱이 뜨는지 확인한다 (엔티티와 테이블이 맞는지 validate, 읽기만 함).
 * .env 가 필요해서 CI(GitHub Actions)에서는 빼고 돌린다: ./gradlew test -Pci
 */
@Tag("integration")
@SpringBootTest
class HealingRoomApplicationTests {

	@Test
	void contextLoads() {
	}

}
