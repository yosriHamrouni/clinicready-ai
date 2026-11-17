package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import com.clinicready.IntegrationTest;
import com.clinicready.agent.AgentOrchestrator.Limits;
import com.clinicready.agent.ToolResult.ToolStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.security.Role;

/** FR-E4: repeated or concurrent runs leave exactly one open follow-up per appointment and requirement. */
@IntegrationTest
@ActiveProfiles(StubAiConfig.PROFILE)
class FollowUpDuplicateIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private AgentOrchestrator orchestrator;
    @Autowired
    private AgentRunRepository runs;
    @Autowired
    private AgentTools tools;
    @Autowired
    private ReadinessService readiness;

    private UUID clinic;
    private UUID user;
    private UUID practitioner;
    private UUID type;
    private SecurityContext identity;

    @BeforeEach
    void setUp() {
        model.reset();
        clinic = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Test')", clinic);
        user = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'User Test', 'RECEPTIONIST')""", user, clinic, user + "@d.test");
        practitioner = UUID.randomUUID();
        jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, 'Dr Test')", practitioner, clinic);
        type = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, 'T', 'T')", type, clinic);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(user.toString())
                .claim("clinicId", clinic.toString()).claim("role", Role.RECEPTIONIST.name()).build();
        identity = SecurityContextHolder.createEmptyContext();
        identity.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(identity);
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void threeSequentialRunsLeaveOneOpenTask() {
        UUID appointment = checkedAppointment();
        String create = "{\"appointmentId\":\"" + appointment + "\",\"requirementCode\":\"ADVANCE_PAYMENT\"}";
        for (int i = 0; i < 3; i++) {
            model.script(ScriptedChatModel.toolCall("createFollowUpDraft", create),
                    ScriptedChatModel.text("{\"summary\":\"s\",\"appointments\":[]}"));
            AgentRun run = runs.save(new AgentRun(clinic, user, "Prepare tomorrow"));
            orchestrator.execute(run.getId(), new Limits(15, Duration.ofSeconds(90)));
            assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(AgentRun.Status.SUCCEEDED);
        }
        assertThat(openTasks(appointment)).isEqualTo(1);
    }

    @Test
    void concurrentCallsLeaveOneOpenTaskAndAgreeOnIt() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                UUID appointment = checkedAppointment();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<ToolResult<AgentTools.DraftResult>>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(pool.submit(() -> {
                        SecurityContextHolder.setContext(identity);
                        try {
                            start.await();
                            return tools.createFollowUpDraft(appointment.toString(), "ADVANCE_PAYMENT", null);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }));
                }
                start.countDown();
                ToolResult<AgentTools.DraftResult> a = results.get(0).get();
                ToolResult<AgentTools.DraftResult> b = results.get(1).get();
                assertThat(a.status()).isEqualTo(ToolStatus.OK);
                assertThat(b.status()).isEqualTo(ToolStatus.OK);
                assertThat(a.data().taskId()).isEqualTo(b.data().taskId());
                assertThat(List.of(a.data().outcome(), b.data().outcome())).containsExactlyInAnyOrder("CREATED",
                        "EXISTING");
                assertThat(openTasks(appointment)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theDatabaseRefusesASecondOpenTaskEvenWithoutTheService() {
        UUID appointment = checkedAppointment();
        assertThat(tools.createFollowUpDraft(appointment.toString(), "ADVANCE_PAYMENT", null).status())
                .isEqualTo(ToolStatus.OK);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO follow_up_task (id, clinic_id, appointment_id, patient_id, requirement_code, status,
                    check_status, reason, language, due_at, created_by, created_at, updated_at)
                SELECT gen_random_uuid(), clinic_id, appointment_id, patient_id, requirement_code, 'AWAITING_APPROVAL',
                    check_status, reason, language, due_at, created_by, now(), now()
                FROM follow_up_task WHERE appointment_id = ?""", appointment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID checkedAppointment() {
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Test')",
                patient, clinic, "T-" + patient.toString().substring(0, 8));
        UUID appointment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at)
                VALUES (?, ?, ?, ?, ?, ?)""", appointment, clinic, patient, practitioner, type,
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));
        readiness.run(appointment);
        return appointment;
    }

    private int openTasks(UUID appointment) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM follow_up_task WHERE appointment_id = ?
                  AND status IN ('PROPOSED', 'AWAITING_APPROVAL', 'APPROVED', 'DELIVERED')""", Integer.class,
                appointment);
    }
}
