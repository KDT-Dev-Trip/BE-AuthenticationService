package ac.su.kdt.beauthenticationservice.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Properties;

public class EnvironmentConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource resource = new FileSystemResource(".env");
        if (resource.exists()) {
            loadEnvFile(environment, resource);
        }
    }

    private void loadEnvFile(ConfigurableEnvironment environment, Resource resource) {
        try {
            Properties properties = new Properties();
            properties.load(resource.getInputStream());
            environment.getPropertySources().addLast(new PropertiesPropertySource("env-file", properties));
        } catch (IOException e) {
            // Ignore if .env file doesn't exist
        }
    }
}