package com.clinicready.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Display names of users, so other features can show "edited by" without reaching into user tables. */
@Service
public class UserDirectory {

    private final AppUserRepository users;

    UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    /** The user's full name, only if the user belongs to the given clinic. */
    @Transactional(readOnly = true)
    public Optional<String> fullName(UUID userId, UUID clinicId) {
        return users.findById(userId).filter(u -> u.getClinicId().equals(clinicId)).map(AppUser::getFullName);
    }
}
