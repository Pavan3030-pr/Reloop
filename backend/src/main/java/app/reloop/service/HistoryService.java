package app.reloop.service;

import app.reloop.dto.history.HistoryEntryDto;
import app.reloop.dto.history.HistoryResponse;
import app.reloop.entity.CollectedWaste;
import app.reloop.entity.PickupRequest.PickupStatus;
import app.reloop.exception.BadRequestException;
import app.reloop.repository.CollectedWasteRepository;
import app.reloop.repository.CollectionPartnerRepository;
import app.reloop.repository.WasteCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final CollectedWasteRepository collectedWasteRepository;
    private final CollectionPartnerRepository collectionPartnerRepository;
    private final WasteCategoryRepository wasteCategoryRepository;

    @Transactional(readOnly = true)
    public HistoryResponse history(UUID userId, String material, String status, LocalDate from, LocalDate to,
                                   int page, int size) {
        String categoryCode = normalizeMaterial(material);
        String normalizedStatus = normalizeStatus(status);
        Instant fromInstant = from != null ? from.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new BadRequestException("'from' date must be before 'to' date");
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50));
        Page<CollectedWaste> entries = collectedWasteRepository.searchHistory(
                userId, categoryCode, normalizedStatus, fromInstant, toInstant, pageable);
        BigDecimal totalKg = collectedWasteRepository.sumKgForUser(
                userId, categoryCode, normalizedStatus, fromInstant, toInstant);
        List<HistoryResponse.CategoryTotalDto> byCategory = collectedWasteRepository
                .sumKgByCategoryForUser(userId).stream()
                .map(t -> new HistoryResponse.CategoryTotalDto(t.getCode(), t.getName(),
                        scale(t.getKg())))
                .toList();

        return new HistoryResponse(entries.map(this::toDto), scale(totalKg), byCategory);
    }

    private HistoryEntryDto toDto(CollectedWaste cw) {
        var pickup = cw.getPickupRequest();
        return new HistoryEntryDto(
                cw.getId().toString(),
                pickup.getCode(),
                pickup.getStatus() != null ? pickup.getStatus().name() : null,
                cw.getCategory().getCode(),
                cw.getCategory().getName(),
                cw.getCategory().getColorHex(),
                cw.getQuantityKg(),
                cw.getCollectionDate(),
                cw.getCollector().getOrganizationName(),
                cw.getNotes());
    }

    private String normalizeMaterial(String material) {
        if (material == null || material.isBlank() || "ALL".equalsIgnoreCase(material)) {
            return null;
        }
        String code = material.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        // Reject unknown materials rather than silently returning an empty page,
        // matching how the status filter handles unknown values.
        if (wasteCategoryRepository.findByCodeIgnoreCase(code).isEmpty()) {
            throw new BadRequestException("Unknown material: " + material);
        }
        return code;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return PickupStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown status: " + status);
        }
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
