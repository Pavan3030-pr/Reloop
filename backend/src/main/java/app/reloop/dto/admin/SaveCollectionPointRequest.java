package app.reloop.dto.admin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record SaveCollectionPointRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 12) String pincode,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Size(max = 200) String operatingHours,
        @Size(max = 20) String contactPhone,
        @NotEmpty List<String> materialCodes
) {}
