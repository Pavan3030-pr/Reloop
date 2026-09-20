package app.reloop.dto.pickup;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CollectPickupRequest(
        @NotNull(message = "Actual weight in kg is required")
        @DecimalMin(value = "0.01", message = "Actual quantity must be greater than 0")
        @Digits(integer = 6, fraction = 2) BigDecimal actualQuantityKg,
        @Size(max = 500) String notes
) {}
