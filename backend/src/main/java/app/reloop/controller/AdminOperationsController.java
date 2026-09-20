package app.reloop.controller;

import app.reloop.dto.admin.AdminAnalyticsDto;
import app.reloop.dto.pickup.PickupDto;
import app.reloop.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final AdminService adminService;

    @GetMapping("/pickups")
    public Page<PickupDto> pickups(@RequestParam(name = "status", required = false) String status,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return adminService.listPickups(status, page, size);
    }

    @GetMapping("/analytics")
    public AdminAnalyticsDto analytics() {
        return adminService.analytics();
    }
}
