package app.reloop.dto.scan;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saving a scan. When confidence is supplied the scan is treated as AI-assisted;
 * without it, the scan was classified manually by the user.
 */
public record SaveScanRequest(
        UUID categoryId,
        @Size(max = 200) String detectedItem,
        BigDecimal confidence,
        Boolean recyclable,
        Boolean hazardous,
        @Size(max = 1000) String disposalInstruction,
        @Size(max = 20000) String aiRawResponse
) {}
