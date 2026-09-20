package app.reloop.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveCategoryRequest(
        @NotBlank @Size(min = 2, max = 30)
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "Code must be UPPER_SNAKE_CASE") String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @Size(max = 9) String colorHex,
        @Size(max = 1000) String defaultDisposalInstructions
) {}
