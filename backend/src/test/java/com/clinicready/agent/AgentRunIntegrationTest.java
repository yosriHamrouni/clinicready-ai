package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.clinicready.IntegrationTest;
import com.clinicready.agent.AgentOrchestrator.Limits;
import com.clinicready.security.Role;
import com.jayway.jsonpath.JsonPath;

/**
 * Agent runs end to end with the scripted model: persistence, actions, limits, report validation and the
 * cross-check against readiness. Not transactional: the HTTP run executes on a worker thread.
 */
@IntegrationTest
@ActiveProfiles(StubAiConfig.PROFILE)
class AgentRunIntegrationTest {

    private static final Limits DEFAULT = new Limits(15, Duration.ofSeconds(90));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private AgentOrchestrator orchestrator;
    @Autowired
    private AgentRunRepository runs;
    @Autowired
    private AgentActionRepository actions;

    private Fixture f;

    @BeforeEach
    void setUp() {
        model.reset();
        f = fixture();
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runStartedOverHttpIsPersistedWithActionsAndAValidatedReport() throws Exception {
        model.script(
                ScriptedChatModel.toolCall("getUpcomingAppointments", "{\"dateFrom\":\"%s\",\"dateTo\":\"%s\"}"
                        .formatted(LocalDate.now(), LocalDate.now().plusDays(3))),
                ScriptedChatModel.toolCall("checkReadiness", args(f.appointment)),
                ScriptedChatModel.toolCall("createFollowUpDraft",
                        "{\"appointmentId\":\"%s\",\"requirementCode\":\"CONSENT_FORM\",\"secret\":\"x\"}"
                                .formatted(f.appointment)),
                ScriptedChatModel.text(correctReport()));

        String body = mvc.perform(post("/api/v1/agent-runs").with(as(f.reception)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Prepare tomorrow's appointments for Dr. Test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(JsonPath.read(body, "$.runId"));

        String finished = awaitFinished(runId);
        assertThat(JsonPath.<String>read(finished, "$.status")).isEqualTo("SUCCEEDED");
        assertThat(JsonPath.<Integer>read(finished, "$.toolCalls")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(finished, "$.promptTokens")).isEqualTo(40);
        assertThat(JsonPath.<List<String>>read(finished, "$.actions[*].tool"))
                .containsExactly("getUpcomingAppointments", "checkReadiness", "createFollowUpDraft");
        assertThat(JsonPath.<List<String>>read(finished, "$.actions[*].resultStatus")).containsOnly("OK");
        assertThat(JsonPath.<String>read(finished, "$.actions[2].arguments.secret")).isEqualTo("[redacted]");
        assertThat(JsonPath.<String>read(finished, "$.actions[2].arguments.requirementCode")).isEqualTo("CONSENT_FORM");
        assertThat(JsonPath.<Integer>read(finished, "$.report.counts.incomplete")).isEqualTo(1);
        assertThat(JsonPath.<String>read(finished, "$.report.appointments[0].appointmentId"))
                .isEqualTo(f.appointment.toString());

        assertThat(jdbc.queryForObject("SELECT run_id FROM follow_up_task WHERE appointment_id = ?", UUID.class,
                f.appointment)).isEqualTo(runId);

        // Survives a refresh: a later read (another request) returns the same stored run.
        mvc.perform(get("/api/v1/agent-runs/{id}", runId).with(as(f.reception)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void reportContradictingReadinessFailsTheRun() {
        model.script(ScriptedChatModel.toolCall("checkReadiness", args(f.appointment)),
                ScriptedChatModel.text(correctReport().replace("INCOMPLETE", "READY")));
        AgentRun run = execute(DEFAULT);
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.REPORT_MISMATCH);
        assertThat(run.getStatusReason()).contains("READY").contains("INCOMPLETE");
    }

    @Test
    void appointmentCheckedButLeftOutOfTheReportFailsTheRun() {
        model.script(ScriptedChatModel.toolCall("checkReadiness", args(f.appointment)),
                ScriptedChatModel.text("{\"summary\":\"Nothing to report.\",\"appointments\":[]}"));
        AgentRun run = execute(DEFAULT);
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.REPORT_MISMATCH);
        assertThat(run.getStatusReason()).contains("missing from the report");
    }

    @Test
    void reportThatOmitsABlockingFindingFailsTheRun() {
        model.script(ScriptedChatModel.toolCall("checkReadiness", args(f.appointment)), ScriptedChatModel.text("""
                {"summary":"s","appointments":[{"appointmentId":"%s","status":"INCOMPLETE",
                 "findings":[{"requirementCode":"CONSENT_FORM","status":"MISSING","action":"a"}]}]}"""
                .formatted(f.appointment)));
        assertThat(execute(DEFAULT).getStatus()).isEqualTo(AgentRun.Status.REPORT_MISMATCH);
    }

    @Test
    void invalidFinalOutputFailsTheRunInsteadOfBeingGuessed() {
        model.script(ScriptedChatModel.text("All good, everything is ready!"));
        AgentRun run = execute(DEFAULT);
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.FAILED);
        assertThat(run.getStatusReason()).contains("not valid JSON");
        assertThat(run.getReport()).isNull();
    }

    @Test
    void fencedJsonReportIsAccepted() {
        model.script(ScriptedChatModel.toolCall("checkReadiness", args(f.appointment)),
                ScriptedChatModel.text("```json\n" + correctReport() + "\n```"));
        assertThat(execute(DEFAULT).getStatus()).isEqualTo(AgentRun.Status.SUCCEEDED);
    }

    @Test
    void exceedingTheToolCallLimitEndsTheRun() {
        String call = "{\"appointmentId\":\"" + f.appointment + "\"}";
        model.script(ScriptedChatModel.toolCall("getFollowUpStatus", call),
                ScriptedChatModel.toolCall("getFollowUpStatus", call),
                ScriptedChatModel.toolCall("getFollowUpStatus", call));
        AgentRun run = execute(new Limits(2, Duration.ofSeconds(90)));
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.TOOL_LIMIT_EXCEEDED);
        assertThat(run.getToolCalls()).isEqualTo(2);
        assertThat(actions.findAllByRunIdOrderBySeq(run.getId())).hasSize(2);
    }

    @Test
    void exceedingTheTimeLimitEndsTheRun() {
        model.script(ScriptedChatModel.text("unused"));
        AgentRun run = execute(new Limits(15, Duration.ofMillis(-1)));
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.TIMED_OUT);
        assertThat(model.prompts()).isEmpty();
    }

    @Test
    void unknownToolIsRecordedAndTheRunGoesOn() {
        model.script(ScriptedChatModel.toolCall("approveFollowUp", "{\"taskId\":\"x\"}"),
                ScriptedChatModel.text("{\"summary\":\"Nothing checked.\",\"appointments\":[]}"));
        AgentRun run = execute(DEFAULT);
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.SUCCEEDED);
        assertThat(actions.findAllByRunIdOrderBySeq(run.getId())).singleElement()
                .satisfies(a -> assertThat(a.getResultStatus()).isEqualTo("UNKNOWN_TOOL"));
    }

    @Test
    void runsAreReadableOnlyInTheClinicAndByTheirInitiatorForPractitioners() throws Exception {
        AgentRun run = runs.save(new AgentRun(f.clinic, f.reception.id, "x"));
        mvc.perform(get("/api/v1/agent-runs/{id}", run.getId()).with(as(f.reception))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/agent-runs/{id}", run.getId()).with(as(f.practitioner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/agent-runs/{id}", run.getId()).with(as(f.stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/agent-runs").with(as(f.reception)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"request\":\"  \"}")).andExpect(status().isBadRequest());
    }

    // ---- helpers ----------------------------------------------------------------------------

    private AgentRun execute(Limits limits) {
        actAs(f.reception);
        AgentRun run = runs.save(new AgentRun(f.clinic, f.reception.id, "Prepare tomorrow"));
        orchestrator.execute(run.getId(), limits);
        return runs.findById(run.getId()).orElseThrow();
    }

    private String awaitFinished(UUID runId) throws Exception {
        for (int i = 0; i < 100; i++) {
            String body = mvc.perform(get("/api/v1/agent-runs/{id}", runId).with(as(f.reception)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (!status.equals("QUEUED") && !status.equals("RUNNING")) {
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run did not finish in 10 s");
    }

    private String correctReport() {
        return """
                {"summary":"One appointment is incomplete.","appointments":[{"appointmentId":"%s",
                 "status":"INCOMPLETE","findings":[
                   {"requirementCode":"CONSENT_FORM","status":"MISSING","action":"Ask for the consent form"},
                   {"requirementCode":"EXPECTED_EXAMINATIONS","status":"NEEDS_REVIEW","action":"Enter them"},
                   {"requirementCode":"ADVANCE_PAYMENT","status":"NEEDS_REVIEW","action":"Enter the amount"}]}]}"""
                .formatted(f.appointment);
    }

    private static String args(UUID appointment) {
        return "{\"appointmentId\":\"" + appointment + "\"}";
    }

    private record User(UUID id, UUID clinic, Role role, UUID practitionerId) {
    }

    private record Fixture(UUID clinic, User reception, User practitioner, User stranger, UUID appointment) {
    }

    private Fixture fixture() {
        UUID clinic = clinic();
        UUID practitioner = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", practitioner,
                clinic);
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
                VALUES (?, ?, ?, ?, ?, ?)""", appointment, clinic, patient, practitioner, type,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        return new Fixture(clinic, user(clinic, Role.RECEPTIONIST, null), user(clinic, Role.PRACTITIONER, practitioner),
                user(clinic(), Role.CLINIC_MANAGER, null), appointment);
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
                id, clinic, id + "@runs-test.test", role.name(), practitionerId);
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
