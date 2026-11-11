package com.clinicready.security;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Role permissions from the PRD matrix, checked by services before touching tenant data. Clinic
 * scoping is separate: services filter every query by {@link CurrentUserService#clinicId()}, and
 * apply the narrower scopes noted on {@link Permission}.
 */
@Service
public class AuthorizationService {

    private static final Map<Role, Set<Permission>> MATRIX = Map.of(
            Role.RECEPTIONIST, EnumSet.complementOf(EnumSet.of(
                    Permission.REQUIREMENT_RULES_MANAGE, Permission.USER_MANAGE, Permission.TEMPLATE_MANAGE)),
            Role.PRACTITIONER, EnumSet.of(
                    Permission.PATIENT_READ, Permission.APPOINTMENT_READ, Permission.DOCUMENT_READ,
                    Permission.DOCUMENT_METADATA_EDIT, Permission.AGENT_RUN),
            Role.CLINIC_MANAGER, EnumSet.allOf(Permission.class));

    private final CurrentUserService currentUser;

    AuthorizationService(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    public static boolean allows(Role role, Permission permission) {
        return MATRIX.get(role).contains(permission);
    }

    public boolean can(Permission permission) {
        return allows(currentUser.role(), permission);
    }

    /** @throws ForbiddenException if the current user's role lacks the permission */
    public void require(Permission permission) {
        if (!can(permission)) {
            throw new ForbiddenException();
        }
    }
}
