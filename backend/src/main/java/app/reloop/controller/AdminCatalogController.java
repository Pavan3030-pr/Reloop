package app.reloop.controller;

import app.reloop.dto.admin.SaveCategoryRequest;
import app.reloop.dto.admin.SaveCollectionPointRequest;
import app.reloop.dto.collection.CollectionPointDto;
import app.reloop.dto.waste.WasteCategoryDto;
import app.reloop.security.SecurityUtils;
import app.reloop.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final AdminService adminService;

    // ---------------- collection points ----------------

    @PostMapping("/collection-points")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionPointDto createCollectionPoint(@Valid @RequestBody SaveCollectionPointRequest request) {
        return adminService.createCollectionPoint(SecurityUtils.currentUser(), request);
    }

    @PutMapping("/collection-points/{id}")
    public CollectionPointDto updateCollectionPoint(@PathVariable UUID id,
                                                    @Valid @RequestBody SaveCollectionPointRequest request) {
        return adminService.updateCollectionPoint(id, request);
    }

    @DeleteMapping("/collection-points/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollectionPoint(@PathVariable UUID id) {
        adminService.deleteCollectionPoint(id);
    }

    // ---------------- waste categories ----------------

    @PostMapping("/waste-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public WasteCategoryDto createCategory(@Valid @RequestBody SaveCategoryRequest request) {
        return adminService.createCategory(request);
    }

    @PutMapping("/waste-categories/{id}")
    public WasteCategoryDto updateCategory(@PathVariable UUID id,
                                           @Valid @RequestBody SaveCategoryRequest request) {
        return adminService.updateCategory(id, request);
    }

    @PatchMapping("/waste-categories/{id}/active")
    public WasteCategoryDto setCategoryActive(@PathVariable UUID id,
                                              @RequestBody Map<String, Boolean> body) {
        boolean active = body.getOrDefault("active", true);
        return adminService.setCategoryActive(id, active);
    }
}
