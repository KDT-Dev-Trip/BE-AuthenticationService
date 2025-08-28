package ac.su.kdt.beauthenticationservice.config;

import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            createAdminUser();
        }
    }

    private void createAdminUser() {
        User adminUser = User.builder()
                .id(UUID.randomUUID().toString())
                .email("admin@devtrip.com")
                .passwordHash(passwordEncoder.encode("admin123!"))
                .name("관리자")
                .role(User.UserRole.ADMIN)
                .isActive(true)
                .socialProvider("local")
                .emailVerified(true)
                .build();

        userRepository.save(adminUser);
        log.info("관리자 테스트 계정 생성 완료: admin@devtrip.com / admin123!");
    }
}