package app.reloop.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the REAL AI path (controller → WasteScanService → GeminiService → RestClient →
 * response parsing) against a deterministic local HTTP stub standing in for the Gemini API.
 *
 * Only the remote provider is replaced — production code is untouched, and the E2E flow never
 * depends on network availability. Covers: valid structured response, low-confidence response,
 * malformed provider payload, and provider failure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiAnalysisWithStubProviderTest {

    private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
            + "AAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final AtomicInteger PROVIDER_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> PROVIDER_BODY = new AtomicReference<>("");
    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>("");
    private static final AtomicReference<String> LAST_API_KEY = new AtomicReference<>("");
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>("");
    private static final AtomicInteger PROVIDER_CALLS = new AtomicInteger();

    private static final HttpServer PROVIDER;
    private static final int PORT;

    static {
        try {
            PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            PORT = PROVIDER.getAddress().getPort();
            PROVIDER.createContext("/v1beta/models", exchange -> {
                PROVIDER_CALLS.incrementAndGet();
                LAST_PATH.set(exchange.getRequestURI().getPath());
                LAST_API_KEY.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] payload = PROVIDER_BODY.get().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(PROVIDER_STATUS.get(), payload.length == 0 ? -1 : payload.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(payload);
                }
            });
            PROVIDER.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start Gemini stub provider", e);
        }
    }

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("reloop.gemini.api-key", () -> "stub-test-key");
        registry.add("reloop.gemini.base-url", () -> "http://127.0.0.1:" + PORT);
        registry.add("reloop.gemini.model", () -> "gemini-3.6-flash");
    }

    @AfterAll
    void stopProvider() {
        PROVIDER.stop(0);
    }

    // ------------------------------------------------------------------ provider scripting

    /** Wraps a JSON string the way the Gemini generateContent endpoint does. */
    private static String geminiEnvelope(String modelJson) {
        return """
                {"candidates":[{"content":{"parts":[{"text":%s}]}}]}
                """.formatted(JSON.valueToTree(modelJson).toString());
    }

    private static void providerResponds(int status, String body) {
        PROVIDER_STATUS.set(status);
        PROVIDER_BODY.set(body);
    }

    // ------------------------------------------------------------------ HTTP helpers

    private JsonNode toNode(ResponseEntity<String> response) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node = JSON.createObjectNode();
            node.put("__status", response.getStatusCode().value());
            String body = response.getBody() == null ? "" : response.getBody();
            if (!body.isBlank()) {
                JsonNode parsed = JSON.readTree(body);
                if (parsed.isArray()) {
                    node.set("__array", parsed);
                } else if (parsed.isObject()) {
                    node.setAll((com.fasterxml.jackson.databind.node.ObjectNode) parsed);
                }
            }
            return node;
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable response: " + response.getBody(), e);
        }
    }

    private String registerUser() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String email = "ai-" + UUID.randomUUID().toString().substring(0, 8) + "@reloop.test";
        ResponseEntity<String> response = http.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", "Ai#Passw0rd1", "fullName", "AI Tester"),
                        headers), String.class);
        return toNode(response).path("accessToken").asText();
    }

    private JsonNode analyze(String token, byte[] bytes, String filename, String mime) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return toNode(http.exchange("/api/waste/analyze", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class));
    }

    private JsonNode saveScan(String token, String categoryId, String detectedItem, String confidence, String imageBase64) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("categoryId", categoryId);
        body.add("detectedItem", detectedItem);
        body.add("confidence", confidence);
        body.add("recyclable", "true");
        body.add("hazardous", "false");
        body.add("disposalInstruction", "Rinse and dry, then use the plastic stream.");
        if (imageBase64 != null) {
            body.add("image", new ByteArrayResource(Base64.getDecoder().decode(imageBase64)) {
                @Override
                public String getFilename() {
                    return "scan.png";
                }
            });
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return toNode(http.exchange("/api/waste/scans", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class));
    }

    private JsonNode categories(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return toNode(http.exchange("/api/waste/categories", HttpMethod.GET,
                new HttpEntity<>(headers), String.class));
    }

    private String categoryId(String token, String code) {
        JsonNode list = categories(token);
        JsonNode items = list.has("__array") ? list.path("__array") : list;
        for (JsonNode category : items) {
            if (code.equals(category.path("code").asText())) {
                return category.path("id").asText();
            }
        }
        throw new IllegalStateException("Category " + code + " not found in seed data");
    }

    // ------------------------------------------------------------------ tests

    @Test
    void validStructuredAnalysisIsReturnedThenSavedAsAiScan() {
        providerResponds(200, geminiEnvelope("""
                {"item":"Plastic water bottle","category":"PLASTIC","confidence":0.91,
                 "recyclable":true,"hazardous":false,
                 "disposalInstruction":"Empty and rinse the bottle, then place it in the plastic stream."}
                """));
        String token = registerUser();

        JsonNode analysis = analyze(token, Base64.getDecoder().decode(PNG_1X1), "bottle.png", "image/png");
        assertThat(analysis.path("__status").asInt()).isEqualTo(200);
        assertThat(analysis.path("item").asText()).isEqualTo("Plastic water bottle");
        assertThat(analysis.path("categoryCode").asText()).isEqualTo("PLASTIC");
        assertThat(analysis.path("categoryName").asText()).isEqualTo("Plastic");
        assertThat(analysis.path("confidence").asDouble()).isEqualTo(0.91);
        assertThat(analysis.path("recyclable").asBoolean()).isTrue();
        assertThat(analysis.path("hazardous").asBoolean()).isFalse();
        assertThat(analysis.path("lowConfidence").asBoolean()).isFalse();
        assertThat(analysis.path("aiModel").asText()).isEqualTo("gemini-3.6-flash");

        // The real service built a real Gemini-shaped request: model in path, key header, inline image data.
        assertThat(LAST_PATH.get()).contains("gemini-3.6-flash").contains(":generateContent");
        assertThat(LAST_API_KEY.get()).isEqualTo("stub-test-key");
        assertThat(LAST_REQUEST_BODY.get())
                .contains("inline_data")
                .contains("image/png")
                // JSON mode is requested so the reply can be parsed deterministically ...
                .contains("\"response_mime_type\":\"application/json\"")
                // ... and the deprecated sampling parameters are never sent.
                .doesNotContain("temperature")
                .doesNotContain("top_p")
                .doesNotContain("top_k");

        // The user confirms the AI result; it is persisted as an AI-assisted scan.
        JsonNode saved = saveScan(token, categoryId(token, "PLASTIC"), analysis.path("item").asText(),
                analysis.path("confidence").asText(), PNG_1X1);
        assertThat(saved.path("__status").asInt()).isEqualTo(201);
        assertThat(saved.path("source").asText()).isEqualTo("AI");
        assertThat(saved.path("confidence").asDouble()).isEqualTo(0.91);
        assertThat(saved.path("category").path("code").asText()).isEqualTo("PLASTIC");
        assertThat(saved.path("imageUrl").asText()).contains("/uploads/");
    }

    @Test
    void lowConfidenceAndUnknownCategoryAreFlaggedAndFallBackToOther() {
        providerResponds(200, geminiEnvelope("""
                {"item":"Blurry object","category":"SOMETHING_ODD","confidence":0.22,
                 "recyclable":false,"hazardous":false,
                 "disposalInstruction":"Not clearly identifiable."}
                """));
        String token = registerUser();

        JsonNode analysis = analyze(token, Base64.getDecoder().decode(PNG_1X1), "blur.png", "image/png");
        assertThat(analysis.path("__status").asInt()).isEqualTo(200);
        assertThat(analysis.path("lowConfidence").asBoolean()).isTrue();
        assertThat(analysis.path("confidence").asDouble()).isEqualTo(0.22);
        // Unknown category codes never leak through; they resolve to OTHER.
        assertThat(analysis.path("categoryCode").asText()).isEqualTo("OTHER");
    }

    @Test
    void malformedProviderPayloadYields503NotFabricatedResult() {
        providerResponds(200, geminiEnvelope("this is not json at all"));
        String token = registerUser();

        JsonNode analysis = analyze(token, Base64.getDecoder().decode(PNG_1X1), "x.png", "image/png");
        assertThat(analysis.path("__status").asInt()).isEqualTo(503);
        assertThat(analysis.path("message").asText()).containsIgnoringCase("AI analysis");
        assertThat(analysis.has("item")).isFalse();
    }

    @Test
    void providerHttpFailureYields503() {
        providerResponds(500, "{\"error\":{\"message\":\"internal\"}}");
        String token = registerUser();

        JsonNode analysis = analyze(token, Base64.getDecoder().decode(PNG_1X1), "x.png", "image/png");
        assertThat(analysis.path("__status").asInt()).isEqualTo(503);
        assertThat(analysis.path("message").asText()).containsIgnoringCase("temporarily unavailable");

        providerResponds(200, ""); // restore for other tests
    }

    @Test
    void nonImageUploadIsRejectedWithoutCallingTheProvider() {
        String token = registerUser();
        int callsBefore = PROVIDER_CALLS.get();
        byte[] notAnImage = "this is plainly not an image".getBytes(StandardCharsets.UTF_8);

        JsonNode analysis = analyze(token, notAnImage, "payload.png", "image/png");

        assertThat(analysis.path("__status").asInt()).isEqualTo(400);
        assertThat(analysis.path("message").asText()).containsIgnoringCase("JPG, PNG, or WEBP");
        assertThat(analysis.has("item")).isFalse();
        // Validation happens before the provider is contacted, so no quota is spent.
        assertThat(PROVIDER_CALLS.get()).isEqualTo(callsBefore);
    }

    @Test
    void missingImagePartIsRejectedWith400NotServerError() {
        String token = registerUser();
        int callsBefore = PROVIDER_CALLS.get();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("notTheImage", "oops");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        JsonNode response = toNode(http.exchange("/api/waste/analyze", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class));

        assertThat(response.path("__status").asInt()).isEqualTo(400);
        assertThat(response.path("message").asText()).containsIgnoringCase("image");
        assertThat(PROVIDER_CALLS.get()).isEqualTo(callsBefore);
    }

    @Test
    void analysisRequiresAuthentication() {
        providerResponds(200, geminiEnvelope("""
                {"item":"Bottle","category":"PLASTIC","confidence":0.9,"recyclable":true,
                 "hazardous":false,"disposalInstruction":"Rinse."}
                """));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(Base64.getDecoder().decode(PNG_1X1)) {
            @Override
            public String getFilename() {
                return "x.png";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = http.exchange("/api/waste/analyze", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
