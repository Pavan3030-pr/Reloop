package app.reloop.dto.scan;

import app.reloop.dto.waste.WasteCategoryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScanDto(
        UUID id,
        String imageUrl,
        String detectedItem,
        WasteCategoryDto category,
        BigDecimal confidence,
        Boolean recyclable,
        Boolean hazardous,
        String disposalInstruction,
        String source,
        Instant createdAt
) {}
