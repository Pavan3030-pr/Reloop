package app.reloop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "reloop")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        Gemini gemini,
        Storage storage,
        Admin admin,
        boolean devMode
) {

    public record Jwt(
            String secret,
            Integer accessTokenTtlMinutes,
            Integer refreshTokenTtlDays
    ) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Gemini(
            String apiKey,
            String model,
            String baseUrl,
            Integer timeoutSeconds
    ) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Storage(
            String type,
            String localDir,
            String publicBaseUrl
    ) {}

    public record Admin(String email, String password) {}
}
