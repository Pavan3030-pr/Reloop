package app.reloop.dto.scan;

import java.math.BigDecimal;

/**
 * Response of POST /api/waste/analyze. Not persisted; the client shows it
 * and lets the user confirm or correct before saving.
 */
public record WasteAnalysisResponse(
        String item,
        String categoryCode,
        String categoryName,
        BigDecimal confidence,
        boolean recyclable,
        boolean hazardous,
        String disposalInstruction,
        boolean lowConfidence,
        String aiModel,
        String aiRawResponse
) {}
