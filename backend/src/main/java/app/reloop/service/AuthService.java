package app.reloop.service;

import app.reloop.config.AppProperties;
import app.reloop.dto.auth.AuthResponse;
import app.reloop.dto.auth.ForgotPasswordRequest;
import app.reloop.dto.auth.ForgotPasswordResponse;
import app.reloop.dto.auth.LoginRequest;
import app.reloop.dto.auth.RefreshRequest;
import app.reloop.dto.auth.RegisterRequest;
import app.reloop.dto.auth.ResetPasswordRequest;
import app.reloop.dto.auth.UserDto;
import app.reloop.entity.PasswordResetToken;
import app.reloop.entity.RefreshToken;
import app.reloop.entity.User;
import app.reloop.entity.UserProfile;
import app.reloop.exception.BadRequestException;
import app.reloop.exception.ConflictException;
import app.reloop.exception.ForbiddenException;
import app.reloop.exception.NotFoundException;
import app.reloop.repository.PasswordResetTokenRepository;
import app.reloop.repository.UserProfileRepository;
import app.reloop.repository.UserRepository;
import app.reloop.security.JwtService;
import app.reloop.security.PasswordService;
import app.reloop.security.RefreshTokenService;
import app.reloop.security.Role;
import app.reloop.security.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long RESET_TOKEN_TTL_MINUTES = 30;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFullName(request.fullName().trim());
        profile.setPhone(request.phone() == null ? null : request.phone().trim());
        userProfileRepository.save(profile);

        notificationService.create(user, "WELCOME",
                "Welcome to ReLoop",
                "Give every piece of waste a better destination. Start by scanning your first item.");

        log.info("Registered new user with email={}", maskEmail(email));
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ForbiddenException("Invalid email or password"));

        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            throw new ForbiddenException("Invalid email or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("This account is disabled. Contact support for help.");
        }

        user.setLastLoginAt(Instant.now());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RefreshRotation rotation = refreshTokenService.rotate(request.refreshToken());
        User user = rotation.user();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenException("This account is disabled. Contact support for help.");
        }
        return buildAuthResponse(user, rotation.refreshToken());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @Transactional(readOnly = true)
    public UserDto me() {
        User current = app.reloop.security.SecurityUtils.currentUser();
        return UserDto.from(current);
    }

    /**
     * Always returns a generic response so account existence cannot be probed.
     * If SMTP is configured an email would be sent; in dev mode the raw token is
     * included in the response so the flow can be completed without email infra.
     */
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase();
        String devToken = null;
        var userOpt = userRepository.findByEmailIgnoreCase(email);
        if (userOpt.isPresent()) {
            String raw = randomToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(userOpt.get());
            token.setTokenHash(sha256(raw));
            token.setExpiresAt(Instant.now().plusSeconds(RESET_TOKEN_TTL_MINUTES * 60));
            passwordResetTokenRepository.save(token);
            log.info("Password reset requested for email={} (token stored hashed)", maskEmail(email));
            if (properties.devMode()) {
                devToken = raw;
            }
        }
        return new ForgotPasswordResponse(
                "If an account exists for this email, password reset instructions have been sent.",
                devToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(sha256(request.token()))
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link"));
        if (!token.isValid()) {
            throw new BadRequestException("This reset link is invalid, expired, or already used");
        }
        User user = token.getUser();
        user.setPasswordHash(passwordService.hash(request.newPassword()));
        token.setUsedAt(Instant.now());
        refreshTokenService.revokeAllFor(user);
        log.info("Password reset completed for email={}", maskEmail(user.getEmail()));
    }

    private AuthResponse buildAuthResponse(User user) {
        return buildAuthResponse(user, refreshTokenService.issue(user));
    }

    private AuthResponse buildAuthResponse(User user, String refreshToken) {
        String accessToken = jwtService.issueAccessToken(user);
        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenTtlSeconds(),
                UserDto.from(user));
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

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
