package app.reloop.security;

import app.reloop.config.AppProperties;
import app.reloop.entity.RefreshToken;
import app.reloop.entity.User;
import app.reloop.exception.UnauthorizedException;
import app.reloop.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties properties;

    @Transactional
    public String issue(User user) {
        String raw = randomToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256(raw));
        entity.setExpiresAt(Instant.now().plusSeconds(refreshTtlSeconds()));
        refreshTokenRepository.save(entity);
        return raw;
    }

    /** Validates a presented refresh token, revokes it, and issues a fresh one (rotation). */
    @Transactional
    public RefreshRotation rotate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!stored.isActive()) {
            throw new UnauthorizedException("Refresh token expired or revoked. Please sign in again.");
        }
        User user = stored.getUser();
        stored.setRevokedAt(Instant.now());
        String nextRaw = issue(user);
        return new RefreshRotation(user, nextRaw);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void revokeAllFor(User user) {
        refreshTokenRepository.revokeAllForUser(user, Instant.now());
    }

    public long refreshTtlSeconds() {
        Integer days = properties.jwt() != null ? properties.jwt().refreshTokenTtlDays() : null;
        return (days != null ? days : 30) * 24L * 3600L;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RefreshRotation(User user, String refreshToken) {}
}
