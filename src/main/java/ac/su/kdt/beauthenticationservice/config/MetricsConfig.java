package ac.su.kdt.beauthenticationservice.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            registry.config()
                    .meterFilter(MeterFilter.deny(id -> {
                        String name = id.getName();
                        return name.startsWith("jvm.") || name.startsWith("system.") || name.startsWith("process.");
                    }))
                    .commonTags(
                            "service", "authentication-service",
                            "version", "1.0.0"
                    );
        };
    }
}