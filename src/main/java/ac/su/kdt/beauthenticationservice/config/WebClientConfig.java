package ac.su.kdt.beauthenticationservice.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(30));
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    // WebFlux 환경에서 RestTemplateBuilder가 없을 수 있으므로 직접 생성
    // @Bean
    // public RestTemplate restTemplate(RestTemplateBuilder builder) {
    //     return builder
    //         .setConnectTimeout(Duration.ofSeconds(5))
    //         .setReadTimeout(Duration.ofSeconds(30))
    //         .build();
    // }
}