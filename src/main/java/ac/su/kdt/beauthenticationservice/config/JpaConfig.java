package ac.su.kdt.beauthenticationservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "ac.su.kdt.beauthenticationservice.repository")
@EntityScan(basePackages = "ac.su.kdt.beauthenticationservice.model.entity")
@EnableTransactionManagement
public class JpaConfig {
}