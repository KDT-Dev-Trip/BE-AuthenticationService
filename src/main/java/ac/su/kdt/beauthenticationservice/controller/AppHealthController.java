package ac.su.kdt.beauthenticationservice.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

@RestController
public class AppHealthController {
    @GetMapping("/app-health")
    public String healthCheck() {
        return "app_health_status 1.0\n# EOF";
    }

    @GetMapping(value = "/metrics"
        , produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String sendMetrics() {
        int randomValue = ThreadLocalRandom.current().nextInt(0, 101); // 0에서 100 사이의 랜덤 정수값 생성

        return String.format("""
        app_health_status 1.0
        random_custom_metric_1 %d
        # EOF
        """, randomValue);
    }
}