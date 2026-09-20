package app.reloop.controller;

import app.reloop.dto.collection.ApplyCollectorRequest;
import app.reloop.dto.collection.CollectionPartnerDto;
import app.reloop.security.SecurityUtils;
import app.reloop.service.CollectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collectors")
@RequiredArgsConstructor
public class CollectorApplicationController {

    private final CollectorService collectorService;

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionPartnerDto apply(@Valid @RequestBody ApplyCollectorRequest request) {
        return collectorService.apply(SecurityUtils.currentUser(), request);
    }

    @GetMapping("/me")
    public CollectionPartnerDto myApplication() {
        return collectorService.myApplication(SecurityUtils.currentUser());
    }
}
