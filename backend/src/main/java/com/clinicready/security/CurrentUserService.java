package com.clinicready.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * The authenticated user of the current request, read from the validated JWT.
 * Tenant-owned queries must use {@link #clinicId()}, never a request parameter.
 */
@Service
public class CurrentUserService {

    public UUID userId() {
        return UUID.fromString(jwt().getSubject());
    }

    public UUID clinicId() {
        return UUID.fromString(jwt().getClaimAsString(JwtService.CLAIM_CLINIC_ID));
    }

    public Role role() {
        return Role.valueOf(jwt().getClaimAsString(JwtService.CLAIM_ROLE));
    }

    /** The practitioner this user is, if any. Only set for users linked to a practitioner. */
    public Optional<UUID> practitionerId() {
        return Optional.ofNullable(jwt().getClaimAsString(JwtService.CLAIM_PRACTITIONER_ID)).map(UUID::fromString);
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        throw new UnauthenticatedException();
    }
}
