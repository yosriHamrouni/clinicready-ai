package com.clinicready.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.appointment.Appointment;
import com.clinicready.appointment.AppointmentService;
import com.clinicready.appointment.dto.NewAppointment;
import com.clinicready.common.ApiException;
import com.clinicready.common.Language;
import com.clinicready.common.NotFoundException;
import com.clinicready.patient.Patient;
import com.clinicready.patient.PatientService;
import com.clinicready.patient.dto.NewPatient;

/**
 * Services enforce clinic isolation and role permissions on their own, whatever controller or
 * agent tool calls them. Each test runs in a rolled-back transaction.
 */
@IntegrationTest
@Transactional
class ClinicScopingIntegrationTest {

    private static final Instant DAY = Instant.parse("2026-11-12T00:00:00Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-11-13T00:00:00Z");

    @Autowired
    private PatientService patients;

    @Autowired
    private AppointmentService appointments;

    @Autowired
    private JdbcTemplate jdbc;

    private Tenant a;
    private Tenant b;
    /** A second patient of clinic A, seen only by a second practitioner. */
    private UUID otherPatientOfA;
    private UUID otherAppointmentOfA;

    @BeforeEach
    void seed() {
        a = tenant("A");
        b = tenant("B");
        otherPatientOfA = insertPatient(a.clinic, "P-0002");
        otherAppointmentOfA = insertAppointment(a.clinic, otherPatientOfA, insertPractitioner(a.clinic), a.type);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userOfClinicAGetsNotFoundForEveryClinicBResource() {
        for (Role role : Role.values()) {
            signIn(role, a.clinic, a.practitioner);

            assertNotFound(() -> patients.get(b.patient));
            assertNotFound(() -> appointments.get(b.appointment));
        }
    }

    @Test
    void listsOnlyContainTheCurrentClinicsRows() {
        signIn(Role.CLINIC_MANAGER, a.clinic, null);

        assertThat(patients.list()).extracting(Patient::getId).containsExactly(a.patient, otherPatientOfA);
        assertThat(appointments.list(DAY, NEXT_DAY)).extracting(Appointment::getId)
                .containsExactlyInAnyOrder(a.appointment, otherAppointmentOfA);
    }

    @Test
    void appointmentCannotReferenceAnotherClinicsRows() {
        signIn(Role.RECEPTIONIST, a.clinic, null);

        assertNotFound(() -> appointments.create(new NewAppointment(b.patient, a.practitioner, a.type, DAY)));
        assertNotFound(() -> appointments.create(new NewAppointment(a.patient, b.practitioner, a.type, DAY)));
        assertNotFound(() -> appointments.create(new NewAppointment(a.patient, a.practitioner, b.type, DAY)));
    }

    @Test
    void createdRowsBelongToTheCurrentUsersClinic() {
        signIn(Role.RECEPTIONIST, a.clinic, null);

        Patient patient = patients.create(new NewPatient("P-NEW", "New Patient", null, Language.AR));
        Appointment appointment = appointments.create(
                new NewAppointment(patient.getId(), a.practitioner, a.type, DAY.plusSeconds(3600)));

        assertThat(patient.getClinicId()).isEqualTo(a.clinic);
        assertThat(appointment.getClinicId()).isEqualTo(a.clinic);
    }

    @Test
    void practitionerReadsOnlyTheirOwnAppointments() {
        signIn(Role.PRACTITIONER, a.clinic, a.practitioner);

        assertThat(appointments.list(DAY, NEXT_DAY)).extracting(Appointment::getId).containsExactly(a.appointment);
        assertThat(appointments.get(a.appointment).getId()).isEqualTo(a.appointment);
        assertNotFound(() -> appointments.get(otherAppointmentOfA));
    }

    @Test
    void practitionerReadsOnlyPatientsTheyHaveAnAppointmentWith() {
        signIn(Role.PRACTITIONER, a.clinic, a.practitioner);

        assertThat(patients.list()).extracting(Patient::getId).containsExactly(a.patient);
        assertThat(patients.get(a.patient).getId()).isEqualTo(a.patient);
        assertNotFound(() -> patients.get(otherPatientOfA));
    }

    @Test
    void practitionerRoleWithoutPractitionerLinkSeesNothing() {
        signIn(Role.PRACTITIONER, a.clinic, null);

        assertThat(appointments.list(DAY, NEXT_DAY)).isEmpty();
        assertThat(patients.list()).isEmpty();
        assertNotFound(() -> appointments.get(a.appointment));
        assertNotFound(() -> patients.get(a.patient));
    }

    @Test
    void practitionerCannotCreatePatientsOrAppointments() {
        signIn(Role.PRACTITIONER, a.clinic, a.practitioner);

        assertThatThrownBy(() -> patients.create(new NewPatient("P-X", "X", null, Language.FR)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> appointments.create(new NewAppointment(a.patient, a.practitioner, a.type, DAY)))
                .isInstanceOf(ForbiddenException.class);
        assertThat(patients.get(a.patient).getId()).isEqualTo(a.patient);
    }

    @Test
    void servicesRejectCallsWithoutAnAuthenticatedUser() {
        List<Executable> calls = List.of(
                () -> patients.list(),
                () -> patients.get(a.patient),
                () -> appointments.list(DAY, NEXT_DAY),
                () -> appointments.get(a.appointment));

        for (Executable call : calls) {
            assertThatThrownBy(call::execute)
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("UNAUTHENTICATED"));
        }
    }

    private static void assertNotFound(Executable call) {
        assertThatThrownBy(call::execute).isInstanceOf(NotFoundException.class);
    }

    /** Signs in a real user row: writes are audited, and audit events reference their actor. */
    private void signIn(Role role, UUID clinicId, UUID practitionerId) {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?)""",
                userId, clinicId, userId + "@scoping-test.test", role.name());
        Jwt.Builder jwt = Jwt.withTokenValue("test")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim(JwtService.CLAIM_CLINIC_ID, clinicId.toString())
                .claim(JwtService.CLAIM_ROLE, role.name());
        if (practitionerId != null) {
            jwt.claim(JwtService.CLAIM_PRACTITIONER_ID, practitionerId.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    private Tenant tenant(String name) {
        UUID clinic = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, ?)", clinic, "Clinique " + name);
        UUID practitioner = insertPractitioner(clinic);
        UUID patient = insertPatient(clinic, "P-0001");
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'CONSULT', 'Consult')",
                type, clinic);
        return new Tenant(clinic, practitioner, patient, type, insertAppointment(clinic, patient, practitioner, type));
    }

    private UUID insertPractitioner(UUID clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", id, clinic);
        return id;
    }

    private UUID insertPatient(UUID clinic, String ref) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Test')",
                id, clinic, ref);
        return id;
    }

    private UUID insertAppointment(UUID clinic, UUID patient, UUID practitioner, UUID type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, clinic, patient, practitioner, type, Timestamp.from(DAY.plusSeconds(9 * 3600)));
        return id;
    }

    private record Tenant(UUID clinic, UUID practitioner, UUID patient, UUID type, UUID appointment) {
    }
}
