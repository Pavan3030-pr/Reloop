package app.reloop.integration;

import app.reloop.config.AppProperties;
import app.reloop.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;

/**
 * Calls Google Gemini (generateContent) to classify a waste photo into structured JSON.
 * The API key never leaves the backend. Failures surface as 503 so the client can
 * fall back to manual classification - results are never fabricated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private static final String PROMPT = """
            You are a waste classification assistant for a recycling platform.
            Identify the main waste item in the photo and reply with STRICT JSON only (no markdown, no prose)
            matching exactly this shape:
            {
              "item": "short human-readable item name",
              "category": "one of PLASTIC, PAPER, CARDBOARD, METAL, GLASS, E_WASTE, HAZARDOUS, ORGANIC, TEXTILE, OTHER",
              "confidence": 0.0 to 1.0,
              "recyclable": true or false,
              "hazardous": true or false,
              "disposalInstruction": "one clear sentence on how to dispose of or prepare this item for collection"
            }
            If the photo does not clearly contain a waste item, set confidence to a low value (<= 0.3)
            and describe what you see in "item". Never invent categories outside the list.
            """;

    private final RestClient geminiRestClient;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return properties.gemini() != null && properties.gemini().isConfigured();
    }

    public GeminiAnalysis analyze(byte[] imageBytes, String mimeType) {
        if (!isConfigured()) {
            throw new ServiceUnavailableException(
                    "AI analysis is not configured on this server. You can classify the item manually instead.");
        }
        try {
            ObjectNode body = buildRequest(imageBytes, mimeType);
            String model = properties.gemini().model() == null ? "gemini-2.0-flash" : properties.gemini().model();
            String response = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", properties.gemini().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return parseResponse(response);
        } catch (RestClientException e) {
            log.warn("Gemini call failed: {}", e.getMessage());
            throw new ServiceUnavailableException(
                    "AI analysis is temporarily unavailable. Please try again later or classify the item manually.");
        } catch (Exception e) {
            log.warn("Gemini response could not be processed: {}", e.getMessage());
            throw new ServiceUnavailableException(
                    "AI analysis returned an unexpected result. Please try again or classify the item manually.");
        }
    }

    private ObjectNode buildRequest(byte[] imageBytes, String mimeType) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", PROMPT);
        ObjectNode inline = parts.addObject().putObject("inline_data");
        inline.put("mime_type", mimeType == null ? "image/jpeg" : mimeType);
        inline.put("data", Base64.getEncoder().encodeToString(imageBytes));
        ObjectNode generationConfig = root.putObject("generation_config");
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.put("temperature", 0.2);
        return root;
    }

    GeminiAnalysis parseResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new IllegalStateException("Empty Gemini candidate text");
            }
            JsonNode data = objectMapper.readTree(text.asText());
            String item = data.path("item").asText("").trim();
            String category = data.path("category").asText("").trim().toUpperCase().replace('-', '_').replace(' ', '_');
            double confidence = data.path("confidence").asDouble(0.0);
            boolean recyclable = data.path("recyclable").asBoolean(false);
            boolean hazardous = data.path("hazardous").asBoolean(false);
            String disposal = data.path("disposalInstruction").asText("").trim();
            if (item.isBlank()) {
                throw new IllegalStateException("Gemini returned no item name");
            }
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            return new GeminiAnalysis(item, category, confidence, recyclable, hazardous, disposal, confidence < 0.5);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Gemini response parse failed: " + e.getMessage(), e);
        }
    }

    public record GeminiAnalysis(
            String item,
            String category,
            double confidence,
            boolean recyclable,
            boolean hazardous,
            String disposalInstruction,
            boolean lowConfidence
    ) {}
}
