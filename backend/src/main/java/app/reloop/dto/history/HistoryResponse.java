package app.reloop.dto.history;

import app.reloop.dto.common.PageResponse;

import java.math.BigDecimal;
import java.util.List;

public record HistoryResponse(
        PageResponse<HistoryEntryDto> entries,
        BigDecimal totalKg,
        List<CategoryTotalDto> byCategory
) {
    public record CategoryTotalDto(String code, String name, BigDecimal kg) {}
}
