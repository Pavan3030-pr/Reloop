package app.reloop.dto.pickup;

import app.reloop.dto.waste.WasteCategoryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PickupDto(
        String code,
        String status,
        WasteCategoryDto category,
        BigDecimal estimatedQuantityKg,
        BigDecimal actualQuantityKg,
        String address,
        String city,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDate pickupDate,
        String timeSlot,
        String photoUrl,
        String notes,
        String requesterName,
        String collectorOrganization,
        Instant scheduledAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant processingAt,
        Instant recoveredAt,
        Instant cancelledAt,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {}
