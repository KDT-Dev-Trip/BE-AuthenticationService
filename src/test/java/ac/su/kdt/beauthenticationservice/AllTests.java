package ac.su.kdt.beauthenticationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Authentication Service Test Suite")
public class AllTests {
    
    @Test
    @DisplayName("테스트 스위트는 gradle test 명령어로 실행하세요")
    void testSuiteInfo() {
        // 모든 테스트는 ./gradlew test 명령어로 실행
        // 특정 테스트는 --tests 옵션 사용
        // 예: ./gradlew test --tests "*IntegrationTest"
    }
}