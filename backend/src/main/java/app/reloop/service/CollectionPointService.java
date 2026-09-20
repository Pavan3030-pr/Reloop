package app.reloop.service;

import app.reloop.dto.collection.CollectionPointDto;
import app.reloop.entity.CollectionPoint;
import app.reloop.entity.WasteCategory;
import app.reloop.exception.NotFoundException;
import app.reloop.repository.CollectionPointRepository;
import app.reloop.repository.WasteCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollectionPointService {

    private final CollectionPointRepository collectionPointRepository;
    private final WasteCategoryRepository wasteCategoryRepository;

    /**
     * Public search. Verified points come only from admin-created records, verified
     * collectors, or imported trusted data - they are never invented here.
     */
    @Transactional(readOnly = true)
    public List<CollectionPointDto> search(String materialCode, String city, String query,
                                           Double lat, Double lng, Double radiusKm) {
        String normalizedMaterial = materialCode == null || materialCode.isBlank() ? null : materialCode.trim();
        String normalizedCity = city == null || city.isBlank() ? null : city.trim();
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();

        List<CollectionPoint> points = collectionPointRepository.search(normalizedMaterial, normalizedCity, normalizedQuery);

        List<CollectionPointDto> dtos = points.stream()
                .map(point -> toDto(point, lat, lng))
                .toList();

        if (lat != null && lng != null) {
            double radius = radiusKm != null && radiusKm > 0 ? radiusKm : 100.0;
            return dtos.stream()
                    .filter(dto -> dto.distanceKm() != null && dto.distanceKm() <= radius)
                    .sorted(Comparator.comparingDouble(CollectionPointDto::distanceKm))
                    .toList();
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public CollectionPointDto get(UUID id, Double lat, Double lng) {
        CollectionPoint point = collectionPointRepository.findById(id)
                .filter(CollectionPoint::isActive)
                .orElseThrow(() -> new NotFoundException("Collection point not found"));
        return toDto(point, lat, lng);
    }

    public CollectionPointDto toDto(CollectionPoint point, Double lat, Double lng) {
        Double distance = null;
        if (lat != null && lng != null) {
            distance = GeoUtils.distanceKm(lat, lng,
                    GeoUtils.toDouble(point.getLatitude(), 0), GeoUtils.toDouble(point.getLongitude(), 0));
            distance = Math.round(distance * 10.0) / 10.0;
        }
        List<CollectionPointDto.MaterialDto> materials = point.getMaterials().stream()
                .map(c -> new CollectionPointDto.MaterialDto(c.getCode(), c.getName(), c.getColorHex()))
                .sorted(Comparator.comparing(CollectionPointDto.MaterialDto::code))
                .toList();
        return new CollectionPointDto(
                point.getId().toString(),
                point.getName(),
                point.getAddress(),
                point.getCity(),
                point.getPincode(),
                point.getLatitude(),
                point.getLongitude(),
                point.getOperatingHours(),
                point.getContactPhone(),
                point.isVerified(),
                point.getSource() != null ? point.getSource().name() : null,
                materials,
                distance);
    }
}
