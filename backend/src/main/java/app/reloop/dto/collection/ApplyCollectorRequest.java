package app.reloop.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ApplyCollectorRequest(
        @NotBlank @Size(max = 160) String organizationName,
        @NotBlank @Size(max = 120) String contactPerson,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 80) String city,
        @Size(max = 12) String pincode,
        @Size(max = 200) String operatingHours,
        @Size(max = 80) String registrationNumber,
        @NotEmpty List<String> materialCodes
) {}
