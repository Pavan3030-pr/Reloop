package app.reloop.security;

import app.reloop.config.AppProperties;
import app.reloop.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates short-lived JWT access tokens (HS256).
 * Refresh tokens are opaque values persisted as SHA-256 hashes by {@link RefreshTokenService}.
 */
@Component
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";

    private final AppProperties properties;

    private SecretKey key;

    @PostConstruct
    void init() {
        String secret = properties.jwt() != null ? properties.jwt().secret() : null;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("""
                    JWT_SECRET is not configured, so access tokens cannot be signed.
                    Local development: copy backend/.env.example to backend/.env and set
                        JWT_SECRET=<openssl rand -base64 48>
                    Spring Boot loads backend/.env automatically, so `./mvnw spring-boot:run`
                    from the backend directory then works without shell exports. You can also
                    export JWT_SECRET directly in your environment.
                    There is deliberately no built-in default: tokens signed with a shared
                    fallback secret would be forgeable by anyone running this code.""");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes for HS256. Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        int ttlMinutes = properties.jwt().accessTokenTtlMinutes() != null
                ? properties.jwt().accessTokenTtlMinutes() : 15;
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlMinutes * 60L);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        int ttlMinutes = properties.jwt().accessTokenTtlMinutes() != null
                ? properties.jwt().accessTokenTtlMinutes() : 15;
        return ttlMinutes * 60L;
    }

    /** Returns the parsed claims of a valid, non-expired token; empty if invalid. */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<UUID> extractUserId(String token) {
        return parse(token).map(Claims::getSubject).map(UUID::fromString);
    }
}
