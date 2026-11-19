package com.clinicready;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.common.ConflictException;
import com.clinicready.followup.FollowUpService;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * MVP exit criteria (YOS-37) that need no model: every forbidden role × action is denied, no follow-up is
 * executed without an approval on any path, and the administrative rules hold (evidence never confirms a
 * payment, a reminder satisfies nothing, human confirmations are traceable, clinics are isolated).
 */
@IntegrationTest
@Transactional
class MvpAcceptanceIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final byte[] PDF = ("%PDF-1.4\n% synthetic test document\n").getBytes();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("clinicready.documents.storage-path", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("clinicready-acceptance-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FollowUpService followUps;
    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    // ---- permission matrix ------------------------------------------------------------------

    @Test
    void everyForbiddenRoleActionPairIsDenied() throws Exception {
        Fixture f = fixture();
        UUID any = UUID.randomUUID();
        String rule = "{\"documentCategory\":\"LAB_RESULT\",\"mandatory\":true,\"validityDays\":null,"
                + "\"needsVerification\":false}";
        String newAppointment = "{\"patientId\":\"%s\",\"practitionerId\":\"%s\",\"appointmentTypeId\":\"%s\",\"startsAt\":\"%s\"}"
                .formatted(any, any, any, Instant.now().plus(2, ChronoUnit.DAYS));

        Map<Role, List<MockHttpServletRequestBuilder>> forbidden = Map.of(
                Role.PRACTITIONER, List.of(
                        json(post("/api/v1/patients"), "{\"fullName\":\"New Patient\"}"),
                        json(post("/api/v1/appointments"), newAppointment),
                        json(put("/api/v1/appointments/{id}/admin-details", f.appointment), "{}"),
                        post("/api/v1/appointments/{id}/payment-confirmation", f.appointment),
                        delete("/api/v1/appointments/{id}/payment-confirmation", f.appointment),
                        post("/api/v1/appointments/{id}/trusted-contact-confirmation", f.appointment),
                        multipart("/api/v1/patients/{id}/documents", f.patient)
                                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", PDF))
                                .param("category", "OTHER"),
                        json(post("/api/v1/appointment-types/{id}/requirements", f.type), rule),
                        json(put("/api/v1/requirements/{id}", f.rule), rule),
                        delete("/api/v1/requirements/{id}", f.rule),
                        post("/api/v1/follow-up-tasks/{id}/approve", f.task),
                        json(post("/api/v1/follow-up-tasks/{id}/reject", f.task), "{\"comment\":\"no\"}"),
                        post("/api/v1/follow-up-tasks/{id}/delivered", f.task),
                        post("/api/v1/follow-up-tasks/{id}/resolve", f.task)),
                Role.RECEPTIONIST, List.of(
                        json(post("/api/v1/appointment-types/{id}/requirements", f.type), rule),
                        json(put("/api/v1/requirements/{id}", f.rule), rule),
                        delete("/api/v1/requirements/{id}", f.rule)));

        for (var entry : forbidden.entrySet()) {
            User user = entry.getKey() == Role.PRACTITIONER ? f.practitioner : f.reception;
            for (MockHttpServletRequestBuilder request : entry.getValue()) {
                mvc.perform(request.with(as(user))).andExpect(status().isForbidden());
            }
        }
        assertThat(jdbc.queryForObject("SELECT payment_confirmed_at IS NULL FROM appointment WHERE id = ?",
                Boolean.class, f.appointment)).isTrue();
    }

    // ---- approval enforcement ---------------------------------------------------------------

    @Test
    void noPathExecutesAFollowUpWithoutAnApproval() throws Exception {
        Fixture f = fixture();
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/delivered", f.task).with(as(f.reception)))
                .andExpect(status().isConflict());

        actAs(f.reception);
        assertThatThrownBy(() -> followUps.markDelivered(f.task)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> followUps.executeApproved(f.task, UUID.randomUUID(), null))
                .isInstanceOf(ConflictException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM follow_up_task WHERE id = ?", String.class, f.task))
                .isEqualTo("AWAITING_APPROVAL");
    }

    // ---- administrative rules ---------------------------------------------------------------

    @Test
    void uploadingEvidenceNeverConfirmsAPayment() throws Exception {
        Fixture f = fixture();
        mvc.perform(put("/api/v1/appointments/{id}/admin-details", f.appointment).with(as(f.reception))
                .contentType(MediaType.APPLICATION_JSON).content("{\"advanceAmount\":120.5}"))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/patients/{id}/documents", f.patient)
                        .file(new MockMultipartFile("file", "receipt.pdf", "application/pdf", PDF))
                        .param("category", "OTHER").with(as(f.reception)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/appointments/{id}/readiness", f.appointment).with(as(f.reception)))
                .andExpect(jsonPath("$.checks[?(@.requirement=='ADVANCE_PAYMENT')].status").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.checks[?(@.requirement=='ADVANCE_PAYMENT')].confirmations[0]").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT payment_confirmed_at IS NULL FROM appointment WHERE id = ?",
                Boolean.class, f.appointment)).isTrue();
    }

    @Test
    void approvingAndDeliveringAReminderSatisfiesNoRequirement() throws Exception {
        Fixture f = fixture();
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/approve", f.task).with(as(f.reception)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/delivered", f.task).with(as(f.reception)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/{id}/readiness", f.appointment).with(as(f.reception)))
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.checks[0].requirement").value("DOCUMENT"))
                .andExpect(jsonPath("$.checks[0].status").value("MISSING"));
    }

    @Test
    void humanConfirmationsAndTheirCorrectionsAreTraceable() throws Exception {
        Fixture f = fixture();
        mvc.perform(put("/api/v1/appointments/{id}/admin-details", f.appointment).with(as(f.reception))
                .contentType(MediaType.APPLICATION_JSON).content("{\"advanceAmount\":100}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/{id}/payment-confirmation", f.appointment).with(as(f.reception)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/{id}/readiness", f.appointment).with(as(f.reception)))
                .andExpect(jsonPath("$.checks[?(@.requirement=='ADVANCE_PAYMENT')].confirmations[0].confirmedBy")
                        .value(f.reception.id.toString()));
        mvc.perform(delete("/api/v1/appointments/{id}/payment-confirmation", f.appointment).with(as(f.manager)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/{id}/readiness", f.appointment).with(as(f.reception)))
                .andExpect(jsonPath("$.checks[?(@.requirement=='ADVANCE_PAYMENT')].status").value("NEEDS_REVIEW"));

        entityManager.flush();
        List<Map<String, Object>> trail = jdbc.queryForList("""
                SELECT action, actor_user_id FROM audit_event WHERE entity_id = ? AND action LIKE 'PAYMENT%'
                ORDER BY occurred_at""", f.appointment);
        assertThat(trail).extracting(e -> e.get("action"))
                .containsExactly("PAYMENT_CONFIRMED", "PAYMENT_CONFIRMATION_REVOKED");
        assertThat(trail).extracting(e -> e.get("actor_user_id")).containsExactly(f.reception.id, f.manager.id);
    }

    @Test
    void rulesRecordsAndEvidenceStayInsideTheirClinic() throws Exception {
        Fixture f = fixture();
        User stranger = user(clinic(), Role.CLINIC_MANAGER, null);
        mvc.perform(get("/api/v1/appointment-types/{id}/requirements", f.type).with(as(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/requirements/{id}", f.rule).with(as(stranger)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"documentCategory\":\"LAB_RESULT\",\"mandatory\":false,\"validityDays\":null,"
                        + "\"needsVerification\":false}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/appointments/{id}/readiness", f.appointment).with(as(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/patients/{id}/documents", f.patient).with(as(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/follow-up-tasks/{id}", f.task).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/approve", f.task).with(as(stranger)))
                .andExpect(status().isNotFound());
    }

    // ---- fixture ----------------------------------------------------------------------------

    private record User(UUID id, UUID clinic, Role role, UUID practitionerId) {
    }

    private record Fixture(User reception, User manager, User practitioner, UUID type, UUID rule, UUID patient,
            UUID appointment, UUID task) {
    }

    private Fixture fixture() throws Exception {
        UUID clinic = clinic();
        UUID practitionerId = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", practitionerId,
                clinic);
        User reception = user(clinic, Role.RECEPTIONIST, null);
        User manager = user(clinic, Role.CLINIC_MANAGER, null);
        User practitioner = user(clinic, Role.PRACTITIONER, practitionerId);
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'MRI', 'IRM')", type,
                clinic);
        String ruleBody = mvc.perform(post("/api/v1/appointment-types/{id}/requirements", type).with(as(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"documentCategory":"CONSENT_FORM","mandatory":true,"validityDays":null,
                                 "needsVerification":false}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                patient, clinic, "T-" + patient.toString().substring(0, 8));
        UUID appointment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""", appointment, clinic, patient, practitionerId, type,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        mvc.perform(post("/api/v1/appointments/{id}/readiness-checks", appointment).with(as(reception)))
                .andExpect(status().isOk());
        actAs(reception);
        UUID task = followUps.createDraft(appointment, "CONSENT_FORM", null, null).task().getId();
        SecurityContextHolder.clearContext();
        return new Fixture(reception, manager, practitioner, type, UUID.fromString(JsonPath.read(ruleBody, "$.id")),
                patient, appointment, task);
    }

    private UUID clinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", id);
        return id;
    }

    private User user(UUID clinic, Role role, UUID practitionerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, practitioner_id)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?, ?)""",
                id, clinic, id + "@acceptance-test.test", role.name(), practitionerId);
        return new User(id, clinic, role, practitionerId);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor as(User user) {
        return jwt().jwt(j -> claims(j, user));
    }

    private static void actAs(User user) {
        Jwt.Builder jwt = Jwt.withTokenValue("test").header("alg", "none");
        claims(jwt, user);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    private static void claims(Jwt.Builder j, User user) {
        j.subject(user.id.toString()).claim("clinicId", user.clinic.toString()).claim("role", user.role.name());
        if (user.practitionerId != null) {
            j.claim("practitionerId", user.practitionerId.toString());
        }
    }
}
