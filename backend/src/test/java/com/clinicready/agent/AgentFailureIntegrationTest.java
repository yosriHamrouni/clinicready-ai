package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.clinicready.IntegrationTest;
import com.clinicready.agent.AgentOrchestrator.Limits;
import com.clinicready.appointment.AppointmentFilter;
import com.clinicready.appointment.AppointmentService;
import com.clinicready.followup.FollowUpService;
import com.clinicready.security.Role;

/**
 * Fault injection (YOS-33): model timeout, tool exceptions on reads and writes, extraction failure, and the
 * model being down. Every run ends with a status and a reason; nothing is left RUNNING.
 */
@IntegrationTest
@ActiveProfiles(StubAiConfig.PROFILE)
class AgentFailureIntegrationTest {

    private static final Limits LIMITS = new Limits(15, Duration.ofSeconds(90));
    private static final String EMPTY_REPORT = "{\"summary\":\"Nothing checked.\",\"appointments\":[]}";

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
    @MockitoSpyBean
    private AppointmentService appointments;
    @MockitoSpyBean
    private FollowUpService followUps;

    private UUID clinic;
    private UUID user;
    private UUID appointment;
    private UUID patient;

    @BeforeEach
    void setUp() {
        model.reset();
        clinic = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", clinic);
        user = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', 'RECEPTIONIST')""", user, clinic, user + "@f.test");
        UUID practitioner = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", practitioner, clinic);
        UUID type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'T', 'T')", type, clinic);
        patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                patient, clinic, "T-" + patient.toString().substring(0, 8));
        appointment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""", appointment, clinic, patient, practitioner, type,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(user.toString())
                .claim("clinicId", clinic.toString()).claim("role", Role.RECEPTIONIST.name()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void modelTimeoutEndsTheRunCleanly() {
        model.script(ScriptedChatModel.failure(
                new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"))));
        AgentRun run = execute();
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.FAILED);
        assertThat(run.getStatusReason()).contains("model call failed").contains("ResourceAccessException");
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void aReadThatThrowsOnceIsRetriedAndTheRunGoesOn() {
        doThrow(new IllegalStateException("db blip")).doCallRealMethod().when(appointments)
                .search(any(AppointmentFilter.class));
        model.script(upcoming(), ScriptedChatModel.text(EMPTY_REPORT));
        AgentRun run = execute();
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.SUCCEEDED);
        assertThat(actions.findAllByRunIdOrderBySeq(run.getId())).extracting(AgentAction::getResultStatus)
                .containsExactly("EXCEPTION", "OK");
    }

    @Test
    void aReadThatThrowsTwiceStopsTheRunAndSaysWhich() {
        doThrow(new IllegalStateException("db down")).when(appointments).search(any(AppointmentFilter.class));
        model.script(upcoming(), ScriptedChatModel.text(EMPTY_REPORT));
        AgentRun run = execute();
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.FAILED);
        assertThat(run.getStatusReason()).contains("getUpcomingAppointments").contains("retried once");
        assertThat(actions.findAllByRunIdOrderBySeq(run.getId())).hasSize(2);
    }

    @Test
    void aWriteThatThrowsIsNotRetried() {
        doThrow(new IllegalStateException("db down")).when(followUps).createDraft(any(), any(), any(), any());
        model.script(ScriptedChatModel.toolCall("createFollowUpDraft",
                "{\"appointmentId\":\"" + appointment + "\",\"requirementCode\":\"ADVANCE_PAYMENT\"}"),
                ScriptedChatModel.text(EMPTY_REPORT));
        AgentRun run = execute();
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.FAILED);
        assertThat(run.getStatusReason()).contains("createFollowUpDraft").contains("not retried");
        assertThat(actions.findAllByRunIdOrderBySeq(run.getId())).singleElement()
                .satisfies(a -> assertThat(a.getResultStatus()).isEqualTo("EXCEPTION"));
    }

    @Test
    void extractionFailureIsAnAnswerNotACrash() {
        UUID document = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO document (id, clinic_id, patient_id, category, source, version, original_filename,
                                      content_type, size_bytes, sha256, storage_key, uploaded_by, extraction_status,
                                      extraction_failure, extracted_at)
                VALUES (?, ?, ?, 'OTHER', 'STAFF_UPLOAD', 1, 'scan.png', 'image/png', 10, ?, ?, ?,
                        'EXTRACTION_FAILED', 'UNREADABLE', now())""",
                document, clinic, patient, "c".repeat(64), clinic + "/" + document, user);
        model.script(ScriptedChatModel.toolCall("inspectDocument", "{\"documentId\":\"" + document + "\"}"),
                ScriptedChatModel.text(EMPTY_REPORT));
        AgentRun run = execute();
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.SUCCEEDED);
        assertThat(actions.findAllByRunIdOrderBySeq(run.getId())).extracting(AgentAction::getResultStatus)
                .containsExactly("ERROR", "ERROR");
    }

    @Test
    void appointmentsDocumentsAndReadinessWorkWhileTheModelIsDown() throws Exception {
        model.script(ScriptedChatModel.failure(new ResourceAccessException("Connection refused")));
        var as = jwt().jwt(j -> j.subject(user.toString()).claim("clinicId", clinic.toString())
                .claim("role", Role.RECEPTIONIST.name()));
        mvc.perform(get("/api/v1/appointments/{id}", appointment).with(as)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/{id}/documents", patient).with(as)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/appointments/{id}/readiness-checks", appointment).with(as))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/appointments/{id}/readiness", appointment).with(as)).andExpect(status().isOk());
    }

    private ScriptedChatModel.Step upcoming() {
        return ScriptedChatModel.toolCall("getUpcomingAppointments", "{\"dateFrom\":\"%s\",\"dateTo\":\"%s\"}"
                .formatted(LocalDate.now(), LocalDate.now().plusDays(2)));
    }

    private AgentRun execute() {
        AgentRun run = runs.save(new AgentRun(clinic, user, "Prepare tomorrow"));
        orchestrator.execute(run.getId(), LIMITS);
        return runs.findById(run.getId()).orElseThrow();
    }
}
