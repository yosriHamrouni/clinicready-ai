package com.clinicready.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.security.Role;

/** Reminder templates (FR/AR, slots only) and the message draft stored with each follow-up task. */
@IntegrationTest
@Transactional
class ReminderTemplateIntegrationTest {

    // 10:30 in Tunis, two days out, so the rendered date is predictable.
    private static final Instant STARTS_AT = ZonedDateTime.now(ZoneId.of("Africa/Tunis")).plusDays(2)
            .withHour(10).withMinute(30).withSecond(0).withNano(0).toInstant();

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

    @Test
    void draftIsBuiltFromTheBuiltInTextInThePatientsLanguage() throws Exception {
        Fixture fr = fixture("FR");
        mvc.perform(get("/api/v1/follow-up-tasks/{id}/draft", task(fr)).with(as(fr.reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("FR"))
                .andExpect(jsonPath("$.templateId").isEmpty())
                .andExpect(jsonPath("$.editedBy").isEmpty())
                .andExpect(jsonPath("$.body").value(ReminderTemplates.fill(ReminderTemplates.BUILT_IN
                        .get(com.clinicready.common.Language.FR), Map.of(
                                "patientName", "Patient Test",
                                "requirement", "le formulaire de consentement signé",
                                "appointmentDate", date(),
                                "clinicName", "Clinique Test"))));

        Fixture ar = fixture("AR");
        String body = mvc.perform(get("/api/v1/follow-up-tasks/{id}/draft", task(ar)).with(as(ar.reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("AR"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("استمارة الموافقة الممضاة").contains("مرحبا Patient Test").doesNotContain("{{");
    }

    @Test
    void managersTemplateWinsThenTheClinicDefault() throws Exception {
        Fixture f = fixture("FR");
        saveTemplate(f.manager, "DEFAULT", "FR", "Rappel {{clinicName}} : {{requirement}}.")
                .andExpect(status().isOk());
        saveTemplate(f.manager, "CONSENT_FORM", "FR", "Bonjour {{ patientName }}, merci de signer {{requirement}}.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementCode").value("CONSENT_FORM"));

        mvc.perform(get("/api/v1/follow-up-tasks/{id}/draft", task(f)).with(as(f.reception)))
                .andExpect(jsonPath("$.body")
                        .value("Bonjour Patient Test, merci de signer le formulaire de consentement signé."))
                .andExpect(jsonPath("$.templateId").isNotEmpty());
        mvc.perform(get("/api/v1/follow-up-tasks/{id}/draft", otherTask(f)).with(as(f.reception)))
                .andExpect(jsonPath("$.body").value("Rappel Clinique Test : la confirmation de l'avance."));

        mvc.perform(get("/api/v1/reminder-templates").with(as(f.manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates.length()").value(2))
                .andExpect(jsonPath("$.slots.length()").value(4));
    }

    @Test
    void templatesAcceptOnlyKnownSlotsAndCodesAndOnlyManagersEditThem() throws Exception {
        Fixture f = fixture("FR");
        saveTemplate(f.manager, "CONSENT_FORM", "FR", "Votre diagnostic : {{diagnosis}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("{{diagnosis}}")));
        saveTemplate(f.manager, "NOT_A_CODE", "FR", "Bonjour").andExpect(status().isBadRequest());
        saveTemplate(f.manager, "CONSENT_FORM", "FR", "   ").andExpect(status().isBadRequest());
        saveTemplate(f.manager, "CONSENT_FORM", "FR", "x".repeat(1001)).andExpect(status().isBadRequest());
        saveTemplate(f.reception, "CONSENT_FORM", "FR", "Bonjour").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reminder-templates").with(as(f.reception))).andExpect(status().isForbidden());

        saveTemplate(f.manager, "CONSENT_FORM", "AR", "مرحبا {{patientName}}").andExpect(status().isOk());
        mvc.perform(delete("/api/v1/reminder-templates/CONSENT_FORM/AR").with(as(f.manager)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/reminder-templates/CONSENT_FORM/AR").with(as(f.manager)))
                .andExpect(status().isNotFound());
        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_type = 'REMINDER_TEMPLATE'",
                String.class)).contains("REMINDER_TEMPLATE_SAVED", "REMINDER_TEMPLATE_DELETED");
    }

    @Test
    void staffEditTheDraftUntilTheTaskIsDecided() throws Exception {
        Fixture f = fixture("FR");
        UUID task = task(f);

        editDraft(f.ownPractitioner, task, "Texte").andExpect(status().isForbidden());
        editDraft(f.reception, task, " ").andExpect(status().isBadRequest());
        editDraft(f.reception, task, "Bonjour, merci d'apporter le formulaire signé.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Bonjour, merci d'apporter le formulaire signé."))
                .andExpect(jsonPath("$.editedBy").value(f.reception.id.toString()));

        mvc.perform(post("/api/v1/follow-up-tasks/{id}/approve", task).with(as(f.manager)))
                .andExpect(status().isOk());
        editDraft(f.reception, task, "Autre texte")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRAFT_LOCKED"));

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT body FROM message_draft WHERE task_id = ?", String.class, task))
                .isEqualTo("Bonjour, merci d'apporter le formulaire signé.");
        assertThat(jdbc.queryForObject(
                "SELECT change_summary::text FROM audit_event WHERE entity_id = ? AND action = 'FOLLOW_UP_DRAFT_EDITED'",
                String.class, task)).as("no message text in the audit trail").doesNotContain("formulaire");
    }

    @Test
    void fillLeavesUnknownSlotsAndQuotesReplacements() {
        assertThat(ReminderTemplates.fill("{{a}} {{patientName}} {{x}}", Map.of("a", "$1\\", "patientName", "P")))
                .isEqualTo("$1\\ P {{x}}");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static String date() {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.of("Africa/Tunis")).format(STARTS_AT);
    }

    private ResultActions saveTemplate(User as, String code, String language, String body) throws Exception {
        return mvc.perform(put("/api/v1/reminder-templates/{code}/{lang}", code, language).with(as(as))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}")); // test bodies hold no quotes or backslashes
    }

    private ResultActions editDraft(User as, UUID task, String body) throws Exception {
        return mvc.perform(put("/api/v1/follow-up-tasks/{id}/draft", task).with(as(as))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"" + body + "\"}")); // test bodies hold no quotes or backslashes
    }

    private UUID task(Fixture f) throws Exception {
        mvc.perform(post("/api/v1/appointments/{id}/readiness-checks", f.appointment).with(as(f.reception)))
                .andExpect(status().isOk());
        actAs(f.reception);
        UUID id = followUps.createDraft(f.appointment, "CONSENT_FORM", null, null).task().getId();
        SecurityContextHolder.clearContext();
        return id;
    }

    private UUID otherTask(Fixture f) {
        actAs(f.reception);
        UUID id = followUps.createDraft(f.appointment, "ADVANCE_PAYMENT", null, null).task().getId();
        SecurityContextHolder.clearContext();
        return id;
    }

    private record User(UUID id, UUID clinic, Role role, UUID practitionerId) {
    }

    private record Fixture(User reception, User manager, User ownPractitioner, UUID appointment) {
    }

    private Fixture fixture(String language) {
        UUID clinic = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", clinic);
        UUID own = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", own, clinic);
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'MRI', 'IRM')", type,
                clinic);
        jdbc.update("""
                INSERT INTO requirement_rule (id, clinic_id, appointment_type_id, document_category, mandatory,
                                              validity_days, needs_verification, created_at, updated_at)
                VALUES (?, ?, ?, 'CONSENT_FORM', true, null, false, now(), now())""", UUID.randomUUID(), clinic, type);
        UUID patient = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patient (id, clinic_id, patient_ref, full_name, preferred_language)
                VALUES (?, ?, ?, 'Patient Test', ?)""", patient, clinic, "T-" + patient.toString().substring(0, 8),
                language);
        UUID appointment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""", appointment, clinic, patient, own, type, Timestamp.from(STARTS_AT));
        return new Fixture(user(clinic, Role.RECEPTIONIST, null), user(clinic, Role.CLINIC_MANAGER, null),
                user(clinic, Role.PRACTITIONER, own), appointment);
    }

    private User user(UUID clinic, Role role, UUID practitionerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, practitioner_id)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?, ?)""",
                id, clinic, id + "@template-test.test", role.name(), practitionerId);
        return new User(id, clinic, role, practitionerId);
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
