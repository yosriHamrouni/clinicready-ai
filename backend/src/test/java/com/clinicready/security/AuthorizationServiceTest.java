package com.clinicready.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The permission matrix from docs/prd.md, cell by cell. */
class AuthorizationServiceTest {

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "RECEPTIONIST,    PATIENT_READ,             true",
            "RECEPTIONIST,    PATIENT_WRITE,            true",
            "RECEPTIONIST,    APPOINTMENT_READ,         true",
            "RECEPTIONIST,    APPOINTMENT_WRITE,        true",
            "RECEPTIONIST,    DOCUMENT_READ,            true",
            "RECEPTIONIST,    DOCUMENT_UPLOAD,          true",
            "RECEPTIONIST,    DOCUMENT_METADATA_EDIT,   true",
            "RECEPTIONIST,    AGENT_RUN,                true",
            "RECEPTIONIST,    FOLLOWUP_APPROVE,         true",
            "RECEPTIONIST,    REQUIREMENT_RULES_MANAGE, false",
            "RECEPTIONIST,    USER_MANAGE,              false",
            "RECEPTIONIST,    TEMPLATE_MANAGE,          false",
            "RECEPTIONIST,    AUDIT_READ,               true",
            "PRACTITIONER,    PATIENT_READ,             true",
            "PRACTITIONER,    PATIENT_WRITE,            false",
            "PRACTITIONER,    APPOINTMENT_READ,         true",
            "PRACTITIONER,    APPOINTMENT_WRITE,        false",
            "PRACTITIONER,    DOCUMENT_READ,            true",
            "PRACTITIONER,    DOCUMENT_UPLOAD,          false",
            "PRACTITIONER,    DOCUMENT_METADATA_EDIT,   true",
            "PRACTITIONER,    AGENT_RUN,                true",
            "PRACTITIONER,    FOLLOWUP_APPROVE,         false",
            "PRACTITIONER,    REQUIREMENT_RULES_MANAGE, false",
            "PRACTITIONER,    USER_MANAGE,              false",
            "PRACTITIONER,    TEMPLATE_MANAGE,          false",
            "PRACTITIONER,    AUDIT_READ,               false",
            "CLINIC_MANAGER,  PATIENT_READ,             true",
            "CLINIC_MANAGER,  PATIENT_WRITE,            true",
            "CLINIC_MANAGER,  APPOINTMENT_READ,         true",
            "CLINIC_MANAGER,  APPOINTMENT_WRITE,        true",
            "CLINIC_MANAGER,  DOCUMENT_READ,            true",
            "CLINIC_MANAGER,  DOCUMENT_UPLOAD,          true",
            "CLINIC_MANAGER,  DOCUMENT_METADATA_EDIT,   true",
            "CLINIC_MANAGER,  AGENT_RUN,                true",
            "CLINIC_MANAGER,  FOLLOWUP_APPROVE,         true",
            "CLINIC_MANAGER,  REQUIREMENT_RULES_MANAGE, true",
            "CLINIC_MANAGER,  USER_MANAGE,              true",
            "CLINIC_MANAGER,  TEMPLATE_MANAGE,          true",
            "CLINIC_MANAGER,  AUDIT_READ,               true"
    })
    void requireEnforcesTheMatrix(Role role, Permission permission, boolean allowed) {
        AuthorizationService authorization = serviceFor(role);

        assertThat(authorization.can(permission)).isEqualTo(allowed);
        if (allowed) {
            assertThatNoException().isThrownBy(() -> authorization.require(permission));
        } else {
            assertThatThrownBy(() -> authorization.require(permission))
                    .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
        }
    }

    @Test
    void everyRoleAndPermissionIsCoveredAbove() {
        assertThat(Role.values().length * Permission.values().length).isEqualTo(39);
    }

    private static AuthorizationService serviceFor(Role role) {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.role()).thenReturn(role);
        return new AuthorizationService(currentUser);
    }
}
