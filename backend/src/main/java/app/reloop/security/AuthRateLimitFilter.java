package app.reloop.security;

import app.reloop.config.AppProperties;
import app.reloop.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force guard for the endpoints that are reachable without a token: login, registration and
 * the two password-reset steps. It applies a sliding-window limit per client IP and answers {@code
 * 429 Too Many Requests} with the standard error envelope plus a {@code Retry-After} header.
 *
 * <p>It is registered ahead of Spring Security on those paths only (see
 * {@link app.reloop.config.RateLimitConfig}), so a flood is rejected before any BCrypt comparison or
 * database lookup happens.
 *
 * <p><b>Scope and limits.</b> The counters live in this JVM only. That is correct for the single
 * instance this build runs as; behind more than one replica a shared store (Redis, a gateway rule)
 * would be needed for a hard guarantee. The client IP is taken from {@code X-Forwarded-For} only
 * because deployments are expected to sit behind a trusted reverse proxy; a directly exposed server
 * should not trust that header. These limitations are repeated in {@code docs/SECURITY.md}.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;

    /**
     * Upper bound on distinct client keys tracked at once. Reached only under a spoofed-source flood;
     * when it is, the whole window map is dropped rather than allowed to grow without limit, which
     * briefly resets counters instead of exhausting memory.
     */
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AppProperties.RateLimit config = properties.rateLimit();
        if (config == null || !config.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = config.perMinuteOrDefault();
        String client = clientIp(request);
        long now = System.currentTimeMillis();

        if (exceedsLimit(client, now, limit)) {
            long retryAfterSeconds = secondsUntilSlotFrees(client, now);
            log.warn("Rate limit exceeded for {} {} from {}", request.getMethod(), request.getRequestURI(), client);
            writeTooManyRequests(response, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean exceedsLimit(String client, long now, int limit) {
        if (hits.size() > MAX_TRACKED_CLIENTS) {
            hits.clear();
        }
        Deque<Long> window = hits.computeIfAbsent(client, key -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, now);
            if (window.size() >= limit) {
                return true;
            }
            window.addLast(now);
            return false;
        }
    }

    private long secondsUntilSlotFrees(String client, long now) {
        Deque<Long> window = hits.get(client);
        if (window == null) {
            return 1;
        }
        synchronized (window) {
            Long oldest = window.peekFirst();
            if (oldest == null) {
                return 1;
            }
            long millis = WINDOW_MILLIS - (now - oldest);
            return Math.max(1, (millis + 999) / 1000);
        }
    }

    private void prune(Deque<Long> window, long now) {
        long cutoff = now - WINDOW_MILLIS;
        Iterator<Long> it = window.iterator();
        while (it.hasNext() && it.next() < cutoff) {
            it.remove();
        }
    }

    /**
     * The address the limit is counted against. {@code X-Forwarded-For} is honoured because ReLoop
     * is deployed behind a reverse proxy; the left-most entry is the original client.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",", 2)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests",
                "Too many attempts from this network. Please wait a moment and try again.")));
    }
}
