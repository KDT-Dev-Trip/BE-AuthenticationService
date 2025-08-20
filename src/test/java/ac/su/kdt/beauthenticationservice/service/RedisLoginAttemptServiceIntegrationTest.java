package ac.su.kdt.beauthenticationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@DisplayName("Redis 로그인 시도 제한 서비스 통합 테스트")
class RedisLoginAttemptServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisLoginAttemptService loginAttemptService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final String testEmail = "test@example.com";
    private final String testIpAddress = "192.168.1.100";

    @BeforeEach
    void setUp() {
        // Redis 데이터 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Nested
    @DisplayName("로그인 실패 시도 기록 테스트")
    class FailedAttemptRecordingTest {

        @Test
        @DisplayName("로그인 실패 시도가 정확히 기록된다")
        void recordFailedAttempt() {
            // when
            loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);

            // then
            int attempts = loginAttemptService.getFailedAttempts(testEmail);
            assertThat(attempts).isEqualTo(1);

            Map<String, Object> lockInfo = loginAttemptService.getAccountLockInfoAsMap(testEmail);
            assertThat(lockInfo.get("failedAttempts")).isEqualTo(1);
            assertThat(lockInfo.get("isLocked")).isEqualTo(false);
        }

        @Test
        @DisplayName("연속된 로그인 실패 시도가 누적된다")
        void recordMultipleFailedAttempts() {
            // when
            for (int i = 1; i <= 5; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);

                // then
                int attempts = loginAttemptService.getFailedAttempts(testEmail);
                assertThat(attempts).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("10회 실패 후 계정이 잠긴다")
        void accountLocksAfter10Failures() {
            // given
            for (int i = 0; i < 9; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);
                assertThat(loginAttemptService.isAccountLocked(testEmail)).isFalse();
            }

            // when - 10번째 시도
            loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);

            // then
            assertThat(loginAttemptService.isAccountLocked(testEmail)).isTrue();
            assertThat(loginAttemptService.getFailedAttempts(testEmail)).isEqualTo(10);

            Map<String, Object> lockInfo = loginAttemptService.getAccountLockInfoAsMap(testEmail);
            assertThat(lockInfo.get("isLocked")).isEqualTo(true);
            assertThat(lockInfo.get("failedAttempts")).isEqualTo(10);
            assertThat(lockInfo.get("lockExpiresAt")).isNotNull();
        }
    }

    @Nested
    @DisplayName("로그인 성공 처리 테스트")
    class SuccessfulLoginTest {

        @Test
        @DisplayName("로그인 성공 시 실패 카운트가 초기화된다")
        void resetFailedAttemptsOnSuccess() {
            // given
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);
            }
            assertThat(loginAttemptService.getFailedAttempts(testEmail)).isEqualTo(5);

            // when
            loginAttemptService.recordSuccessfulLogin(testEmail);

            // then
            assertThat(loginAttemptService.getFailedAttempts(testEmail)).isEqualTo(0);
            assertThat(loginAttemptService.isAccountLocked(testEmail)).isFalse();
        }

        @Test
        @DisplayName("잠긴 계정도 로그인 성공 시 잠금이 해제된다")
        void unlockAccountOnSuccess() {
            // given - 계정을 잠금 상태로 만듦
            for (int i = 0; i < 10; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);
            }
            assertThat(loginAttemptService.isAccountLocked(testEmail)).isTrue();

            // when
            loginAttemptService.recordSuccessfulLogin(testEmail);

            // then
            assertThat(loginAttemptService.isAccountLocked(testEmail)).isFalse();
            assertThat(loginAttemptService.getFailedAttempts(testEmail)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("계정 잠금 해제 테스트")
    class AccountUnlockTest {

        @Test
        @DisplayName("관리자가 수동으로 계정 잠금을 해제할 수 있다")
        void manualUnlockAccount() {
            // given - 계정을 잠금 상태로 만듦
            for (int i = 0; i < 10; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);
            }
            assertThat(loginAttemptService.isAccountLocked(testEmail)).isTrue();

            // when
            loginAttemptService.unlockAccount(testEmail, "test-admin");

            // then
            assertThat(loginAttemptService.isAccountLocked(testEmail)).isFalse();
            assertThat(loginAttemptService.getFailedAttempts(testEmail)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("IP 기반 모니터링 테스트")
    class IpMonitoringTest {

        @Test
        @DisplayName("의심스러운 IP 주소가 기록된다")
        void recordSuspiciousIp() {
            // given
            String suspiciousIp = "192.168.1.200";

            // when
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, suspiciousIp);
            }

            // then
            Map<String, Integer> suspiciousIps = loginAttemptService.getSuspiciousIpAddresses();
            assertThat(suspiciousIps).containsKey(suspiciousIp);
            assertThat(suspiciousIps.get(suspiciousIp)).isGreaterThan(0);
        }

        @Test
        @DisplayName("여러 IP에서의 실패 시도가 독립적으로 처리된다")
        void handleMultipleIpsIndependently() {
            // given
            String ip1 = "192.168.1.100";
            String ip2 = "192.168.1.200";

            // when
            for (int i = 0; i < 3; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, ip1);
            }
            for (int i = 0; i < 7; i++) {
                loginAttemptService.recordFailedAttempt("other@example.com", ip2);
            }

            // then
            Map<String, Integer> suspiciousIps = loginAttemptService.getSuspiciousIpAddresses();
            assertThat(suspiciousIps).containsKeys(ip1, ip2);
            assertThat(suspiciousIps.get(ip1)).isEqualTo(3);
            assertThat(suspiciousIps.get(ip2)).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("보안 통계 테스트")
    class SecurityStatisticsTest {

        @Test
        @DisplayName("보안 통계가 정확히 계산된다")
        void calculateSecurityStatistics() {
            // given
            String email1 = "user1@example.com";
            String email2 = "user2@example.com";
            String email3 = "user3@example.com";

            // user1: 5회 실패
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(email1, "192.168.1.100");
            }

            // user2: 10회 실패 (잠김)
            for (int i = 0; i < 10; i++) {
                loginAttemptService.recordFailedAttempt(email2, "192.168.1.200");
            }

            // user3: 15회 실패 (잠김)
            for (int i = 0; i < 15; i++) {
                loginAttemptService.recordFailedAttempt(email3, "192.168.1.300");
            }

            // when
            Map<String, Object> stats = loginAttemptService.getSecurityStatistics();

            // then
            assertThat(stats.get("totalFailedAttempts")).isEqualTo(30);
            assertThat(stats.get("lockedAccounts")).isEqualTo(2);
            assertThat(stats.get("suspiciousIpCount")).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("시간 기반 만료 테스트")
    class TimeBasedExpirationTest {

        @Test
        @DisplayName("실패 시도 기록이 TTL을 가진다")
        void failedAttemptsHaveTtl() {
            // when
            loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);

            // then
            String key = "login_attempts:" + testEmail;
            Long ttl = redisTemplate.getExpire(key);
            assertThat(ttl).isGreaterThan(0); // TTL이 설정되어 있음
            assertThat(ttl).isLessThanOrEqualTo(3600); // 1시간 이하
        }

        @Test
        @DisplayName("계정 잠금 정보가 TTL을 가진다")
        void accountLockHasTtl() {
            // given
            for (int i = 0; i < 10; i++) {
                loginAttemptService.recordFailedAttempt(testEmail, testIpAddress);
            }

            // then
            String lockKey = "account_lock:" + testEmail;
            Long ttl = redisTemplate.getExpire(lockKey);
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(3600);
        }
    }
}