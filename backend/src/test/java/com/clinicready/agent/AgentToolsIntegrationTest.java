package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.IntegrationTest;
import com.clinicready.agent.AgentTools.PatientDocument;
import com.clinicready.agent.AgentTools.Requirement;
import com.clinicready.agent.AgentTools.UpcomingAppointment;
import com.clinicready.agent.ToolResult.ToolStatus;
import com.clinicready.readiness.AggregateStatus;
import com.clinicready.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Each agent read tool, called as a user: scoping, typed results, and no identity details in outputs. */
@IntegrationTest
@Transactional
class AgentToolsIntegrationTest {

    @Autowired
    private AgentTools tools;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toolsAreRegisteredWithSpringAiAndReturnStatusCodedJson() {
        Fixture f = fixture();
        actAs(f.reception);
        List<ToolCallback> callbacks = Arrays.asList(ToolCallbacks.from(tools));
        assertThat(callbacks).extracting(c -> c.getToolDefinition().name()).contains("getUpcomingAppointments",
                "getAppointmentRequirements", "listPatientDocuments", "checkReadiness", "getFollowUpStatus");

        ToolCallback check = callbacks.stream().filter(c -> c.getToolDefinition().name().equals("checkReadiness"))
                .findFirst().orElseThrow();
        String out = check.call("{\"appointmentId\":\"" + f.ownAppointment + "\"}");
        assertThat(out).contains("\"status\":\"OK\"").contains(f.ownAppointment.toString());
    }

    @Test
    void getUpcomingAppointmentsIsScopedToClinicAndPractitioner() throws Exception {
        Fixture f = fixture();
        String from = LocalDate.now().toString();
        String to = LocalDate.now().plusDays(3).toString();

        actAs(f.reception);
        ToolResult<List<UpcomingAppointment>> all = tools.getUpcomingAppointments(null, from, to);
        assertThat(all.status()).isEqualTo(ToolStatus.OK);
        assertThat(all.data()).extracting(UpcomingAppointment::appointmentId)
                .containsExactlyInAnyOrder(f.ownAppointment, f.otherAppointment);
        assertThat(all.data()).allSatisfy(a -> assertThat(a.patientRef()).startsWith("T-"));
        assertThat(json.writeValueAsString(all)).doesNotContain("Patient Test").doesNotContain("+216");

        actAs(f.practitioner);
        assertThat(tools.getUpcomingAppointments(null, from, to).data())
                .extracting(UpcomingAppointment::appointmentId).containsExactly(f.ownAppointment);

        actAs(f.stranger);
        assertThat(tools.getUpcomingAppointments(null, from, to).data()).isEmpty();

        assertThat(tools.getUpcomingAppointments(null, "tomorrow", to).status()).isEqualTo(ToolStatus.INVALID_INPUT);
        assertThat(tools.getUpcomingAppointments(null, to, from).status()).isEqualTo(ToolStatus.INVALID_INPUT);
    }

    @Test
    void getAppointmentRequirementsListsRulesAndBuiltIns() {
        Fixture f = fixture();
        actAs(f.reception);
        ToolResult<List<Requirement>> result = tools.getAppointmentRequirements(f.type.toString());
        assertThat(result.status()).isEqualTo(ToolStatus.OK);
        assertThat(result.data()).extracting(Requirement::requirementCode)
                .containsExactly("CONSENT_FORM", "EXPECTED_EXAMINATIONS", "ADVANCE_PAYMENT");
        assertThat(result.data().get(0).validityDays()).isEqualTo(30);

        actAs(f.stranger);
        assertThat(tools.getAppointmentRequirements(f.type.toString()).status()).isEqualTo(ToolStatus.NOT_FOUND);
        assertThat(tools.getAppointmentRequirements("not-a-uuid").status()).isEqualTo(ToolStatus.INVALID_INPUT);
    }

    @Test
    void listPatientDocumentsReturnsCategoriesDatesAndConfidenceOnly() throws Exception {
        Fixture f = fixture();
        actAs(f.reception);
        ToolResult<List<PatientDocument>> result = tools.listPatientDocuments(f.patient.toString());
        assertThat(result.status()).isEqualTo(ToolStatus.OK);
        assertThat(result.data()).singleElement().satisfies(d -> {
            assertThat(d.documentId()).isEqualTo(f.document);
            assertThat(d.docDate()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(d.confidence()).isEqualTo(0.42);
            assertThat(d.needsReview()).isTrue();
        });
        assertThat(json.writeValueAsString(result)).doesNotContain("consent.pdf").doesNotContain("Patient Test");

        actAs(f.stranger);
        assertThat(tools.listPatientDocuments(f.patient.toString()).status()).isEqualTo(ToolStatus.NOT_FOUND);
    }

    @Test
    void checkReadinessRunsTheEngineForVisibleAppointmentsOnly() {
        Fixture f = fixture();
        actAs(f.practitioner);
        ToolResult<AgentTools.Readiness> result = tools.checkReadiness(f.ownAppointment.toString());
        assertThat(result.status()).isEqualTo(ToolStatus.OK);
        assertThat(result.data().status()).isEqualTo(AggregateStatus.NEEDS_REVIEW);
        assertThat(result.data().checks()).extracting(AgentTools.ReadinessCheckResult::requirementCode)
                .containsExactly("CONSENT_FORM", "EXPECTED_EXAMINATIONS", "ADVANCE_PAYMENT");
        assertThat(result.data().checks().get(0).evidenceDocumentIds()).containsExactly(f.document);

        assertThat(tools.checkReadiness(f.otherAppointment.toString()).status()).isEqualTo(ToolStatus.NOT_FOUND);
        actAs(f.stranger);
        assertThat(tools.checkReadiness(f.ownAppointment.toString()).status()).isEqualTo(ToolStatus.NOT_FOUND);
    }

    @Test
    void getFollowUpStatusValidatesInputAndScopesTheAppointment() {
        Fixture f = fixture();
        actAs(f.reception);
        assertThat(tools.getFollowUpStatus(null, f.ownAppointment.toString()).status()).isEqualTo(ToolStatus.OK);
        assertThat(tools.getFollowUpStatus(null, null).status()).isEqualTo(ToolStatus.INVALID_INPUT);
        assertThat(tools.getFollowUpStatus(UUID.randomUUID().toString(), f.ownAppointment.toString()).status())
                .isEqualTo(ToolStatus.INVALID_INPUT);

        actAs(f.stranger);
        assertThat(tools.getFollowUpStatus(null, f.ownAppointment.toString()).status())
                .isEqualTo(ToolStatus.NOT_FOUND);
    }

    // ---- fixture ----------------------------------------------------------------------------

    private record User(UUID id, UUID clinic, Role role, UUID practitionerId) {
    }

    private record Fixture(User reception, User practitioner, User stranger, UUID type, UUID patient,
            UUID document, UUID ownAppointment, UUID otherAppointment) {
    }

    private Fixture fixture() {
        UUID clinic = clinic();
        UUID own = practitioner(clinic);
        UUID other = practitioner(clinic);
        User reception = user(clinic, Role.RECEPTIONIST, null);
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'MRI', 'IRM')",
                type, clinic);
        jdbc.update("""
                INSERT INTO requirement_rule (id, clinic_id, appointment_type_id, document_category, mandatory,
                                              validity_days, needs_verification, created_at, updated_at)
                VALUES (?, ?, ?, 'CONSENT_FORM', true, 30, false, now(), now())""", UUID.randomUUID(), clinic, type);
        UUID patient = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO patient (id, clinic_id, patient_ref, full_name, phone)
                VALUES (?, ?, ?, 'Patient Test', '+216 20 000 000')""",
                patient, clinic, "T-" + patient.toString().substring(0, 8));
        UUID document = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document (id, clinic_id, patient_id, category, source, version, original_filename,
                                      content_type, size_bytes, sha256, storage_key, uploaded_by, extraction_status,
                                      extracted_at)
                VALUES (?, ?, ?, 'CONSENT_FORM', 'STAFF_UPLOAD', 1, 'consent.pdf', 'application/pdf', 10, ?, ?, ?,
                        'EXTRACTED', now())""",
                document, clinic, patient, "a".repeat(64), clinic + "/" + document, reception.id);
        jdbc.update("""
                INSERT INTO document_extraction (document_id, clinic_id, status, category, document_date,
                                                 confidence, model_name, review_reason)
                VALUES (?, ?, 'SUCCEEDED', 'CONSENT_FORM', '2026-10-01', 0.42, 'stub', 'LOW_CONFIDENCE')""",
                document, clinic);
        return new Fixture(reception, user(clinic, Role.PRACTITIONER, own), user(clinic(), Role.CLINIC_MANAGER, null),
                type, patient, document, appointment(clinic, patient, own, type), appointment(clinic, patient, other, type));
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
                id, clinic, id + "@tools-test.test", role.name(), practitionerId);
        return new User(id, clinic, role, practitionerId);
    }

    private UUID practitioner(UUID clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", id, clinic);
        return id;
    }

    private UUID appointment(UUID clinic, UUID patient, UUID practitioner, UUID type) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""", id, clinic, patient, practitioner, type,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        return id;
    }

    private static void actAs(User user) {
        Jwt.Builder jwt = Jwt.withTokenValue("test").header("alg", "none").subject(user.id.toString())
                .claim("clinicId", user.clinic.toString()).claim("role", user.role.name());
        if (user.practitionerId != null) {
            jwt.claim("practitionerId", user.practitionerId.toString());
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }
}
