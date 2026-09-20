package app.reloop.dto.auth;

public record ForgotPasswordResponse(
        String message,
        String devResetToken
) {}
