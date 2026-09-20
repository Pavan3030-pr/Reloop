package app.reloop.controller;

import app.reloop.dto.admin.AdminUserDto;
import app.reloop.dto.admin.RejectPartnerRequest;
import app.reloop.dto.admin.UpdateUserStatusRequest;
import app.reloop.dto.collection.CollectionPartnerDto;
import app.reloop.security.SecurityUtils;
import app.reloop.service.AdminService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDirectoryController {

    private final AdminService adminService;

    @GetMapping("/users")
    public Page<AdminUserDto> users(@RequestParam(name = "q", required = false) String q,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return adminService.listUsers(q, page, size);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserDto setUserStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminService.setUserStatus(id, request.status());
    }

    @GetMapping("/collectors")
    public Page<CollectionPartnerDto> collectors(@RequestParam(name = "status", required = false) String status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return adminService.listPartners(status, page, size);
    }

    @PatchMapping("/collectors/{id}/verify")
    public CollectionPartnerDto verifyCollector(@PathVariable UUID id) {
        return adminService.verifyPartner(id, SecurityUtils.currentUser());
    }

    @PatchMapping("/collectors/{id}/reject")
    public CollectionPartnerDto rejectCollector(@PathVariable UUID id,
                                                @Valid @RequestBody RejectPartnerRequest request) {
        return adminService.rejectPartner(id, request.reason(), SecurityUtils.currentUser());
    }
}
