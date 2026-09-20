package app.reloop.controller;

import app.reloop.dto.collection.CollectionPointDto;
import app.reloop.service.CollectionPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collection-points")
@RequiredArgsConstructor
public class CollectionPointController {

    private final CollectionPointService collectionPointService;

    @GetMapping
    public List<CollectionPointDto> list(
            @RequestParam(name = "material", required = false) String material,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm) {
        return collectionPointService.search(material, city, q, lat, lng, radiusKm);
    }

    @GetMapping("/{id}")
    public CollectionPointDto get(@PathVariable String id,
                                  @RequestParam(name = "lat", required = false) Double lat,
                                  @RequestParam(name = "lng", required = false) Double lng) {
        try {
            return collectionPointService.get(java.util.UUID.fromString(id), lat, lng);
        } catch (IllegalArgumentException e) {
            throw new app.reloop.exception.NotFoundException("Collection point not found");
        }
    }
}
