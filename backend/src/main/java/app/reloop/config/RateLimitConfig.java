package app.reloop.config;

import app.reloop.security.AuthRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires {@link AuthRateLimitFilter} onto exactly the unauthenticated auth endpoints, at the earliest
 * filter order so a flood is rejected before Spring Security and before any password hashing.
 */
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter() {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(new AuthRateLimitFilter(properties, objectMapper));
        registration.addUrlPatterns(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/forgot-password",
                "/api/auth/reset-password");
        registration.setName("authRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
