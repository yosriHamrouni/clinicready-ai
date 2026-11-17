package com.clinicready.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
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
import com.clinicready.agent.AgentTools;
import com.clinicready.agent.ToolResult.ToolStatus;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/** Follow-up state machine and approval API: legal paths, illegal transitions, permissions, approval enforcement. */
@IntegrationTest
@Transactional
class FollowUpApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FollowUpService followUps;
    @Autowired
    private AgentTools tools;
    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvedTaskIsDeliveredRecheckedAndResolvedWithAnAuditTrail() throws Exception {
        Fixture f = fixture();
        UUID task = task(f);
        Timestamp before = evaluatedAt(f.appointment);

        decide(f.reception, task, "approve", "{\"comment\":\"ok to send\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].actorId").value(f.reception.id.toString()))
                .andExpect(jsonPath("$.approvals[0].comment").value("ok to send"));
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/delivered", task).with(as(f.manager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredBy").value(f.manager.id.toString()));
        assertThat(evaluatedAt(f.appointment)).as("delivery re-checks readiness").isAfter(before);
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/resolve", task).with(as(f.reception)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE entity_id = ? ORDER BY occurred_at",
                String.class, task)).containsExactly("FOLLOW_UP_TASK_CREATED", "FOLLOW_UP_TASK_APPROVED",
                        "FOLLOW_UP_TASK_DELIVERED", "FOLLOW_UP_TASK_RESOLVED");
    }

    @Test
    void illegalTransitionsAreRejected() throws Exception {
        Fixture f = fixture();
        UUID task = task(f);

        mvc.perform(post("/api/v1/follow-up-tasks/{id}/delivered", task).with(as(f.reception)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/resolve", task).with(as(f.reception)))
                .andExpect(status().isConflict());
        decide(f.reception, task, "reject", null).andExpect(status().isBadRequest());
        decide(f.reception, task, "reject", "{\"comment\":\"patient already sent it\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        decide(f.reception, task, "approve", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/delivered", task).with(as(f.reception)))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyReceptionAndManagersDecideAndVisibilityFollowsTheAppointment() throws Exception {
        Fixture f = fixture();
        UUID task = task(f);

        decide(f.ownPractitioner, task, "approve", null).andExpect(status().isForbidden());
        decide(f.stranger, task, "approve", null).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/follow-up-tasks/{id}", task).with(as(f.ownPractitioner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requirementCode").value("CONSENT_FORM"));
        mvc.perform(get("/api/v1/follow-up-tasks/{id}", task).with(as(f.otherPractitioner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/follow-up-tasks").with(as(f.ownPractitioner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(task.toString()));
        mvc.perform(get("/api/v1/follow-up-tasks").with(as(f.otherPractitioner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/follow-up-tasks").param("status", "AWAITING_APPROVAL").with(as(f.reception)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/follow-up-tasks").with(as(f.stranger)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void submitApprovedActionFailsWithoutAValidHumanApproval() throws Exception {
        Fixture f = fixture();
        UUID task = task(f);
        UUID other = otherTask(f);
        actAs(f.reception);

        assertThat(tools.submitApprovedAction(task.toString(), UUID.randomUUID().toString()).status())
                .as("no approval yet").isEqualTo(ToolStatus.CONFLICT);

        String approved = decide(f.reception, task, "approve", null).andReturn().getResponse().getContentAsString();
        UUID approvalId = UUID.fromString(JsonPath.read(approved, "$.approvalId"));
        String otherApproved = decide(f.reception, other, "approve", null).andReturn().getResponse()
                .getContentAsString();
        UUID otherApprovalId = UUID.fromString(JsonPath.read(otherApproved, "$.approvalId"));
        actAs(f.reception); // a MockMvc request clears the thread's security context when it ends

        assertThat(tools.submitApprovedAction(task.toString(), otherApprovalId.toString()).status())
                .as("another task's approval").isEqualTo(ToolStatus.CONFLICT);
        var executed = tools.submitApprovedAction(task.toString(), approvalId.toString());
        assertThat(executed.status()).isEqualTo(ToolStatus.OK);
        assertThat(executed.data().outcome()).isEqualTo("EXECUTED");
        assertThat(executed.data().status()).isEqualTo(FollowUpStatus.DELIVERED);
        assertThat(tools.submitApprovedAction(task.toString(), approvalId.toString()).status())
                .as("an approval is used once").isEqualTo(ToolStatus.CONFLICT);

        actAs(f.stranger);
        assertThat(tools.submitApprovedAction(other.toString(), otherApprovalId.toString()).status())
                .isEqualTo(ToolStatus.NOT_FOUND);
    }

    @Test
    void noAgentToolCanApproveOrReject() {
        List<String> names = Arrays.stream(ToolCallbacks.from(tools)).map(c -> c.getToolDefinition().name())
                .map(n -> n.toLowerCase(Locale.ROOT)).toList();
        assertThat(names).noneMatch(n -> n.contains("approve") && !n.equals("submitapprovedaction"))
                .noneMatch(n -> n.contains("reject"))
                .noneMatch(n -> n.contains("resolve"))
                .noneMatch(n -> n.contains("deliver"));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private ResultActions decide(User as, UUID task, String decision, String body) throws Exception {
        var request = post("/api/v1/follow-up-tasks/{id}/" + decision, task).with(as(as));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
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

    private Timestamp evaluatedAt(UUID appointment) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT evaluated_at FROM readiness_result WHERE appointment_id = ?",
                Timestamp.class, appointment);
    }

    private record User(UUID id, UUID clinic, Role role, UUID practitionerId) {
    }

    private record Fixture(User reception, User manager, User ownPractitioner, User otherPractitioner, User stranger,
            UUID appointment) {
    }

    private Fixture fixture() {
        UUID clinic = clinic();
        UUID own = practitioner(clinic);
        UUID other = practitioner(clinic);
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'MRI', 'IRM')", type,
                clinic);
        jdbc.update("""
                INSERT INTO requirement_rule (id, clinic_id, appointment_type_id, document_category, mandatory,
                                              validity_days, needs_verification, created_at, updated_at)
                VALUES (?, ?, ?, 'CONSENT_FORM', true, null, false, now(), now())""", UUID.randomUUID(), clinic, type);
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                patient, clinic, "T-" + patient.toString().substring(0, 8));
        UUID appointment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""", appointment, clinic, patient, own, type,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        return new Fixture(user(clinic, Role.RECEPTIONIST, null), user(clinic, Role.CLINIC_MANAGER, null),
                user(clinic, Role.PRACTITIONER, own), user(clinic, Role.PRACTITIONER, other),
                user(clinic(), Role.CLINIC_MANAGER, null), appointment);
    }

    private UUID clinic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", id);
        return id;
    }

    private UUID practitioner(UUID clinic) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", id, clinic);
        return id;
    }

    private User user(UUID clinic, Role role, UUID practitionerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role, practitioner_id)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', ?, ?)""",
                id, clinic, id + "@followup-test.test", role.name(), practitionerId);
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
