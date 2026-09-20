package app.reloop.dto.collection;

import java.math.BigDecimal;
import java.util.List;

public record CollectionPointDto(
        String id,
        String name,
        String address,
        String city,
        String pincode,
        BigDecimal latitude,
        BigDecimal longitude,
        String operatingHours,
        String contactPhone,
        boolean verified,
        String source,
        List<MaterialDto> materials,
        Double distanceKm
) {
    public record MaterialDto(String code, String name, String colorHex) {}
}
