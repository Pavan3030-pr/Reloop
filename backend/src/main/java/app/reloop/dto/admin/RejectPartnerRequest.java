package app.reloop.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectPartnerRequest(
        @NotBlank @Size(max = 500) String reason
) {}
