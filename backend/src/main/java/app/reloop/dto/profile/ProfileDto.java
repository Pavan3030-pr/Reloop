package app.reloop.dto.profile;

import app.reloop.dto.auth.UserDto;

public record ProfileDto(
        UserDto user,
        String fullName,
        String phone,
        String city,
        String addressLine
) {}
