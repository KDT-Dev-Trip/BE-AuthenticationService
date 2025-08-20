package ac.su.kdt.beauthenticationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class BeAuthenticationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BeAuthenticationServiceApplication.class, args);
    }

}
