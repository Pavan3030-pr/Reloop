package app.reloop.controller;

import app.reloop.dto.pickup.CreatePickupRequest;
import app.reloop.dto.pickup.PickupDto;
import app.reloop.security.SecurityUtils;
import app.reloop.service.PickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pickups")
@RequiredArgsConstructor
public class PickupController {

    private final PickupService pickupService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PickupDto create(@Valid @ModelAttribute CreatePickupRequest request,
                            @RequestParam(value = "photo", required = false) MultipartFile photo) {
        return pickupService.create(SecurityUtils.currentUser(), request, photo);
    }

    @GetMapping
    public Page<PickupDto> listMine(@RequestParam(name = "status", required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return pickupService.listMine(SecurityUtils.currentUser(), status, page, size);
    }

    @GetMapping("/{code}")
    public PickupDto get(@PathVariable String code) {
        return pickupService.getByCode(SecurityUtils.currentUser(), code);
    }

    @PatchMapping("/{code}/cancel")
    public PickupDto cancel(@PathVariable String code) {
        return pickupService.cancelByUser(SecurityUtils.currentUser(), code);
    }
}
