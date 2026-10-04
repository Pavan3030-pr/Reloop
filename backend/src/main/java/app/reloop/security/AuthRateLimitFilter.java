package app.reloop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing and reset-token abuse: each client address may call a
 * sensitive auth endpoint a limited number of times per minute. In-memory and per
 * instance, which is enough to stop casual brute force; put a shared limiter in front of
 * multi-instance deployments.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;

    private final boolean enabled;
    private final int maxRequests;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${reloop.rate-limit.enabled:true}") boolean enabled,
            @Value("${reloop.rate-limit.auth-per-minute:20}") int maxRequests) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        long[] window = windows.compute(key, (k, w) -> {
            if (w == null || now - w[0] >= WINDOW_MS) {
                return new long[] {now, 1};
            }
            w[1]++;
            return w;
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue()[0] >= WINDOW_MS);
        }
        if (window[1] > maxRequests) {
            long retryAfter = Math.max(1, (WINDOW_MS - (now - window[0])) / 1000);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":429,\"error\":\"Too many requests\","
                    + "\"message\":\"Too many attempts. Please wait a minute and try again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
