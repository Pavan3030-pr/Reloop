package app.reloop.dto.pickup;

import app.reloop.dto.waste.WasteCategoryDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a collector may see about a request <strong>before</strong> it is assigned to them.
 *
 * <p>A resident's street address, pincode, GPS coordinates, door/gate notes and waste photo are
 * personal data. They are deliberately absent here: an open pool every verified collector can
 * browse is not a reason to hand out one household's precise location to all of them. The full
 * record becomes available through {@link PickupDto} (GET /api/pickups/{code}) only once the
 * request is assigned to that collector.
 *
 * <p>{@code approximateDistanceKm} is rounded to the nearest kilometre on purpose — it is enough
 * to judge whether a job is worth a trip, and too coarse to locate a home.
 */
public record PickupSummaryDto(
        String code,
        String status,
        WasteCategoryDto category,
        BigDecimal estimatedQuantityKg,
        String city,
        LocalDate pickupDate,
        String timeSlot,
        Instant createdAt,
        BigDecimal approximateDistanceKm
) {}
