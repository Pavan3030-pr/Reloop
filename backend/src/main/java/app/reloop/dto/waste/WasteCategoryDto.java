package app.reloop.dto.waste;

import app.reloop.entity.WasteCategory;

import java.util.UUID;

public record WasteCategoryDto(
        UUID id,
        String code,
        String name,
        String description,
        String colorHex,
        String defaultDisposalInstructions,
        boolean active
) {
    public static WasteCategoryDto from(WasteCategory category) {
        return new WasteCategoryDto(category.getId(), category.getCode(), category.getName(),
                category.getDescription(), category.getColorHex(), category.getDefaultDisposalInstructions(),
                category.isActive());
    }
}
