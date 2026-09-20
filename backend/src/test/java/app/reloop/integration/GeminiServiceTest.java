package app.reloop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiServiceTest {

    private final GeminiService service = new GeminiService(null, null, new ObjectMapper());

    private static String generateContentReply(String innerJson) {
        return """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{ "text": %s }]
                    }
                  }]
                }
                """.formatted(new ObjectMapper().valueToTree(innerJson).toString());
    }

    @Test
    void parsesValidGeminiReply() {
        String inner = """
                {"item":"Plastic water bottle","category":"PLASTIC","confidence":0.92,
                 "recyclable":true,"hazardous":false,
                 "disposalInstruction":"Rinse and place in the plastic stream."}
                """;
        GeminiService.GeminiAnalysis a = service.parseResponse(generateContentReply(inner));

        assertEquals("Plastic water bottle", a.item());
        assertEquals("PLASTIC", a.category());
        assertEquals(0.92, a.confidence(), 1e-9);
        assertTrue(a.recyclable());
        assertFalse(a.hazardous());
        assertFalse(a.lowConfidence());
    }

    @Test
    void normalizesCategoryCodes() {
        String inner = """
                {"item":"Old phone charger","category":"e-waste","confidence":0.8,
                 "recyclable":true,"hazardous":false,"disposalInstruction":"Use e-waste drop-off."}
                """;
        assertEquals("E_WASTE", service.parseResponse(generateContentReply(inner)).category());
    }

    @Test
    void clampsConfidenceAndFlagsLowConfidence() {
        String high = """
                {"item":"X","category":"PLASTIC","confidence":1.7,"recyclable":true,
                 "hazardous":false,"disposalInstruction":"ok"}
                """;
        GeminiService.GeminiAnalysis a = service.parseResponse(generateContentReply(high));
        assertEquals(1.0, a.confidence(), 1e-9);
        assertFalse(a.lowConfidence());

        String low = """
                {"item":"Unidentifiable blur","category":"OTHER","confidence":-0.2,"recyclable":false,
                 "hazardous":false,"disposalInstruction":"Ask a collector."}
                """;
        GeminiService.GeminiAnalysis b = service.parseResponse(generateContentReply(low));
        assertEquals(0.0, b.confidence(), 1e-9);
        assertTrue(b.lowConfidence());
    }

    @Test
    void markdownFencedJsonIsRejected() {
        String reply = """
                {"candidates":[{"content":{"parts":[{"text":"```json\\n{\\"item\\":\\"X\\"}\\n```"}]}}]}
                """;
        assertThrows(IllegalStateException.class, () -> service.parseResponse(reply));
    }

    @Test
    void missingItemIsRejected() {
        String reply = generateContentReply(
                "{\"category\":\"PLASTIC\",\"confidence\":0.9,\"recyclable\":true,\"hazardous\":false}");
        assertThrows(IllegalStateException.class, () -> service.parseResponse(reply));
    }

    @Test
    void emptyCandidatesAreRejected() {
        String reply = "{\"candidates\":[]}";
        assertThrows(IllegalStateException.class, () -> service.parseResponse(reply));
    }
}
