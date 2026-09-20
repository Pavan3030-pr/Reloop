package app.reloop.security;

import app.reloop.config.AppProperties;
import app.reloop.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes

    private JwtService serviceWith(int ttlMinutes) {
        JwtService service = new JwtService(new AppProperties(
                new AppProperties.Jwt(SECRET, ttlMinutes, 30), null, null, null, null, false));
        service.init();
        return service;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwt-user@reloop.test");
        user.setRole(Role.USER);
        return user;
    }

    @Test
    void issueAndParseRoundtrip() {
        JwtService service = serviceWith(15);
        User user = user();

        String token = service.issueAccessToken(user);
        Optional<UUID> parsed = service.extractUserId(token);

        assertTrue(parsed.isPresent());
        assertEquals(user.getId(), parsed.get());

        Claims claims = service.parse(token).orElseThrow();
        assertEquals(user.getEmail(), claims.get(JwtService.CLAIM_EMAIL, String.class));
        assertEquals(Role.USER.name(), claims.get(JwtService.CLAIM_ROLE, String.class));
        assertEquals(15 * 60L, service.accessTokenTtlSeconds());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = serviceWith(15);
        String token = service.issueAccessToken(user()) + "x";
        assertTrue(service.parse(token).isEmpty());
        assertTrue(service.extractUserId(token).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = serviceWith(0); // expires immediately
        String token = service.issueAccessToken(user());
        assertTrue(service.parse(token).isEmpty());
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        JwtService service = new JwtService(new AppProperties(
                new AppProperties.Jwt("too-short", 15, 30), null, null, null, null, false));
        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    void missingSecretIsRejectedAtStartup() {
        JwtService service = new JwtService(new AppProperties(
                new AppProperties.Jwt(" ", 15, 30), null, null, null, null, false));
        assertThrows(IllegalStateException.class, service::init);
    }
}
