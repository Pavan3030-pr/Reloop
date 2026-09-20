package app.reloop.dto.pickup;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePickupRequest(
        @NotNull UUID categoryId,
        @NotNull @DecimalMin(value = "0.1", message = "Estimated quantity must be at least 0.1 kg")
        @Digits(integer = 6, fraction = 2) BigDecimal estimatedQuantityKg,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 12) String pincode,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @NotNull @FutureOrPresent(message = "Pickup date cannot be in the past") LocalDate pickupDate,
        @NotBlank @Pattern(regexp = "MORNING|AFTERNOON|EVENING", message = "Time slot must be MORNING, AFTERNOON or EVENING")
        String timeSlot,
        @Size(max = 1000) String notes
) {}
