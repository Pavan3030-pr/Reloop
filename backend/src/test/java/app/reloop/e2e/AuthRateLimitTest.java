package app.reloop.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The brute-force guard on the unauthenticated auth endpoints, verified over real HTTP.
 *
 * <p>The broad suites run with the limiter off (they make many auth calls from one address); this
 * class turns it back on with a deliberately tiny limit. Each test drives its own client address via
 * {@code X-Forwarded-For}, so the tests do not share a counter and their order cannot matter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthRateLimitTest {

    private static final int LIMIT = 3;

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void enableLimiter(DynamicPropertyRegistry registry) {
        registry.add("reloop.rate-limit.enabled", () -> "true");
        registry.add("reloop.rate-limit.auth-requests-per-minute", () -> String.valueOf(LIMIT));
    }

    private ResponseEntity<String> login(String clientIp, String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", clientIp);
        return http.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", "definitely-wrong"), headers), String.class);
    }

    @Test
    void attemptsBeyondTheLimitAreRejectedWith429AndRetryAfter() {
        String ip = "203.0.113.10";

        // The first `LIMIT` attempts reach the controller (and fail as bad credentials, i.e. 401).
        for (int i = 0; i < LIMIT; i++) {
            assertThat(login(ip, "nobody@reloop.test").getStatusCode().value())
                    .as("attempt %s is within the limit", i + 1)
                    .isEqualTo(401);
        }

        ResponseEntity<String> blocked = login(ip, "nobody@reloop.test");
        assertThat(blocked.getStatusCode().value()).isEqualTo(429);
        assertThat(blocked.getBody()).contains("Too many attempts");
        assertThat(blocked.getHeaders().getFirst("Retry-After")).isNotBlank();
    }

    @Test
    void theLimitIsCountedPerClientAddress() {
        String noisy = "203.0.113.20";
        for (int i = 0; i < LIMIT + 1; i++) {
            login(noisy, "nobody@reloop.test");
        }
        // A different address is not penalised by its neighbour's flood.
        assertThat(login("203.0.113.21", "nobody@reloop.test").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void unrelatedEndpointsAreNeverRateLimited() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "203.0.113.30");
        for (int i = 0; i < LIMIT * 3; i++) {
            ResponseEntity<String> response = http.exchange("/api/waste/categories", HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }
    }
}
