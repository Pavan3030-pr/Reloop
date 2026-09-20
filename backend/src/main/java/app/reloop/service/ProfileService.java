package app.reloop.service;

import app.reloop.dto.profile.ProfileDto;
import app.reloop.dto.profile.UpdateProfileRequest;
import app.reloop.dto.auth.UserDto;
import app.reloop.entity.User;
import app.reloop.entity.UserProfile;
import app.reloop.exception.NotFoundException;
import app.reloop.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public ProfileDto get(User user) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return toDto(user, profile);
    }

    @Transactional
    public ProfileDto update(User user, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        profile.setFullName(request.fullName().trim());
        profile.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        profile.setCity(request.city() == null || request.city().isBlank() ? null : request.city().trim());
        profile.setAddressLine(request.addressLine() == null || request.addressLine().isBlank()
                ? null : request.addressLine().trim());
        return toDto(user, profile);
    }

    private ProfileDto toDto(User user, UserProfile profile) {
        return new ProfileDto(UserDto.from(user), profile.getFullName(), profile.getPhone(),
                profile.getCity(), profile.getAddressLine());
    }
}
