package app.reloop.dto.history;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

public record HistoryResponse(
        Page<HistoryEntryDto> entries,
        BigDecimal totalKg,
        List<CategoryTotalDto> byCategory
) {
    public record CategoryTotalDto(String code, String name, BigDecimal kg) {}
}
