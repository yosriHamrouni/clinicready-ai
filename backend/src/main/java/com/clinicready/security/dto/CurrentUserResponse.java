package com.clinicready.security.dto;

import java.util.List;
import java.util.UUID;

import com.clinicready.security.AppUser;
import com.clinicready.security.Role;

public record CurrentUserResponse(UUID id, String email, String fullName, UUID clinicId, List<Role> roles) {

    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getClinicId(), List.of(user.getRole()));
    }
}
