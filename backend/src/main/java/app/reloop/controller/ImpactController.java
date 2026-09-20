package app.reloop.controller;

import app.reloop.dto.impact.ImpactResponse;
import app.reloop.security.SecurityUtils;
import app.reloop.service.ImpactService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/impact")
@RequiredArgsConstructor
public class ImpactController {

    private final ImpactService impactService;

    @GetMapping
    public ImpactResponse impact() {
        return impactService.impactForUser(SecurityUtils.currentUser().getId());
    }
}
