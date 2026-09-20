package app.reloop.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Shared RestClient factory with explicit connect/read timeouts for external integrations.
 */
@Configuration
@RequiredArgsConstructor
public class HttpClientConfig {

    private final AppProperties properties;

    @Bean
    public RestClient geminiRestClient(RestClient.Builder builder) {
        int timeoutSeconds = properties.gemini() != null && properties.gemini().timeoutSeconds() != null
                ? properties.gemini().timeoutSeconds() : 30;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        String baseUrl = properties.gemini() != null && properties.gemini().baseUrl() != null
                ? properties.gemini().baseUrl() : "https://generativelanguage.googleapis.com";
        return builder
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
    }
}
