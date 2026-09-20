package app.reloop.controller;

import app.reloop.dto.profile.ProfileDto;
import app.reloop.dto.profile.UpdateProfileRequest;
import app.reloop.security.SecurityUtils;
import app.reloop.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileDto get() {
        return profileService.get(SecurityUtils.currentUser());
    }

    @PutMapping
    public ProfileDto update(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(SecurityUtils.currentUser(), request);
    }
}
