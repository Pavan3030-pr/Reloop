package app.reloop.controller;

import app.reloop.dto.pickup.CollectPickupRequest;
import app.reloop.dto.pickup.CollectorDashboardDto;
import app.reloop.dto.pickup.PickupDto;
import app.reloop.dto.pickup.PickupSummaryDto;
import app.reloop.dto.pickup.SchedulePickupRequest;
import app.reloop.dto.pickup.StatusUpdateRequest;
import app.reloop.security.SecurityUtils;
import app.reloop.service.CollectorService;
import app.reloop.service.PickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collector operations. Role COLLECTOR is enforced by SecurityConfig;
 * an active VERIFIED partner is enforced inside the service layer.
 */
@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorPickupController {

    private final PickupService pickupService;
    private final CollectorService collectorService;

    /**
     * Work already assigned to this organisation — full detail (address, contact, photo) is
     * appropriate here because the collector has committed to the job.
     */
    @GetMapping(value = "/pickups", params = "scope=mine")
    public Page<PickupDto> myPickups(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        var partner = collectorService.verifiedPartnerOf(SecurityUtils.currentUser());
        return pickupService.assignedTo(partner, page, size);
    }

    /**
     * The open pool, redacted. Optional {@code lat}/{@code lng} add an approximate (1 km-rounded)
     * distance so a collector can judge the trip without learning where anyone lives.
     */
    @GetMapping("/pickups")
    public Page<PickupSummaryDto> availablePickups(
            @RequestParam(name = "lat", required = false) java.math.BigDecimal lat,
            @RequestParam(name = "lng", required = false) java.math.BigDecimal lng,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return pickupService.availableSummaries(lat, lng, page, size);
    }

    @PatchMapping("/pickups/{code}/accept")
    public PickupDto accept(@PathVariable String code) {
        var partner = collectorService.verifiedPartnerOf(SecurityUtils.currentUser());
        return pickupService.accept(partner, code);
    }

    @PatchMapping("/pickups/{code}/schedule")
    public PickupDto schedule(@PathVariable String code, @Valid @RequestBody SchedulePickupRequest request) {
        var partner = collectorService.verifiedPartnerOf(SecurityUtils.currentUser());
        return pickupService.schedule(partner, code, request);
    }

    @PatchMapping("/pickups/{code}/collect")
    public PickupDto collect(@PathVariable String code, @Valid @RequestBody CollectPickupRequest request) {
        var partner = collectorService.verifiedPartnerOf(SecurityUtils.currentUser());
        return pickupService.collect(partner, code, request);
    }

    @PatchMapping("/pickups/{code}/status")
    public PickupDto updateStatus(@PathVariable String code, @Valid @RequestBody StatusUpdateRequest request) {
        return pickupService.updateProcessingStatus(SecurityUtils.currentUser(), code, request);
    }

    @PatchMapping("/pickups/{code}/release")
    public PickupDto release(@PathVariable String code,
                             @RequestParam(name = "reason", required = false) String reason) {
        var partner = collectorService.verifiedPartnerOf(SecurityUtils.currentUser());
        return pickupService.release(partner, code, reason);
    }

    @GetMapping("/dashboard")
    public CollectorDashboardDto dashboard() {
        var partner = collectorService.verifiedPartnerOf(SecurityUtils.currentUser());
        return pickupService.dashboard(partner);
    }
}
