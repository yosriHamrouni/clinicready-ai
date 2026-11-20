package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import com.clinicready.readiness.CheckStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessResponse;
import com.clinicready.seed.DemoData;
import com.clinicready.seed.DemoDataGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * MVP exit criterion end to end, on the demo seed data with the scripted model: "Prepare tomorrow's
 * appointments for Dr. X" → report cross-checked against readiness → follow-up approved → delivered →
 * readiness re-checked → audit trail complete.
 */
@IntegrationTest
@ActiveProfiles({"demo", StubAiConfig.PROFILE})
class PrepareTomorrowEndToEndTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private ReadinessService readiness;
    @Autowired
    private ObjectMapper json;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prepareTomorrowReportApproveDeliverRecheckAudit() throws Exception {
        LocalDate today = LocalDate.now(DemoDataGenerator.CLINIC_ZONE);
        DemoData data = DemoDataGenerator.generate(today);
        UUID clinic = jdbc.queryForObject("SELECT id FROM clinic WHERE name = ?", UUID.class,
                DemoDataGenerator.CLINIC_NAME);
        DemoData.User reception = user(data, "RECEPTIONIST");
        DemoData.User manager = user(data, "CLINIC_MANAGER");

        // "Tomorrow" = the next day with scheduled appointments; Dr. X = a practitioner seeing patients that day.
        List<DemoData.Appointment> upcoming = data.appointments().stream()
                .filter(a -> a.status().equals("SCHEDULED"))
                .filter(a -> a.startsAt().atZoneSameInstant(DemoDataGenerator.CLINIC_ZONE).toLocalDate().isAfter(today))
                .sorted(Comparator.comparing(DemoData.Appointment::startsAt)).toList();
        LocalDate tomorrow = upcoming.get(0).startsAt().atZoneSameInstant(DemoDataGenerator.CLINIC_ZONE).toLocalDate();
        UUID drX = upcoming.get(0).practitionerId();
        List<DemoData.Appointment> targets = upcoming.stream()
                .filter(a -> a.practitionerId().equals(drX))
                .filter(a -> a.startsAt().atZoneSameInstant(DemoDataGenerator.CLINIC_ZONE).toLocalDate().equals(tomorrow))
                .toList();
        assertThat(targets).isNotEmpty().hasSizeLessThanOrEqualTo(13); // 1 list + n checks + 1 draft <= 15 calls
        DemoData.Appointment first = targets.get(0);

        // The manager requires a consent form for the first appointment's type.
        mvc.perform(post("/api/v1/appointment-types/{id}/requirements", first.appointmentTypeId()).with(as(manager, clinic))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"documentCategory":"CONSENT_FORM","mandatory":true,"validityDays":null,
                                 "needsVerification":false}"""))
                .andExpect(status().isCreated());

        // Ground truth from the deterministic engine, and the model's script built from it.
        actAs(reception, clinic);
        List<ScriptedChatModel.Step> script = new ArrayList<>();
        script.add(ScriptedChatModel.toolCall("getUpcomingAppointments",
                "{\"practitionerId\":\"%s\",\"dateFrom\":\"%s\",\"dateTo\":\"%s\"}".formatted(drX, tomorrow, tomorrow)));
        List<Map<String, Object>> reported = new ArrayList<>();
        for (DemoData.Appointment a : targets) {
            ReadinessResponse r = readiness.run(a.id());
            script.add(ScriptedChatModel.toolCall("checkReadiness", "{\"appointmentId\":\"" + a.id() + "\"}"));
            List<Map<String, Object>> findings = new ArrayList<>();
            for (ReadinessCheckResponse c : r.checks()) {
                if (c.mandatory() && c.applicable() && c.status() != CheckStatus.PRESENT) {
                    findings.add(Map.of("requirementCode", c.requirementCode(), "status", c.status().name(),
                            "action", String.valueOf(c.remainingAction())));
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appointmentId", a.id().toString());
            entry.put("status", r.status().name());
            entry.put("findings", findings);
            reported.add(entry);
        }
        SecurityContextHolder.clearContext();
        script.add(ScriptedChatModel.toolCall("createFollowUpDraft",
                "{\"appointmentId\":\"%s\",\"requirementCode\":\"CONSENT_FORM\"}".formatted(first.id())));
        script.add(ScriptedChatModel.text(json.writeValueAsString(
                Map.of("summary", "Tomorrow's appointments checked.", "appointments", reported))));
        model.reset();
        model.script(script.toArray(ScriptedChatModel.Step[]::new));

        // The run.
        String started = mvc.perform(post("/api/v1/agent-runs").with(as(reception, clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Prepare tomorrow's appointments for Dr. X\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(JsonPath.read(started, "$.runId"));
        String run = awaitFinished(runId, reception, clinic);
        assertThat(JsonPath.<String>read(run, "$.status")).as(run).isEqualTo("SUCCEEDED");
        assertThat(JsonPath.<Integer>read(run, "$.toolCalls")).isEqualTo(targets.size() + 2);
        assertThat(JsonPath.<Integer>read(run, "$.report.counts.incomplete")
                + JsonPath.<Integer>read(run, "$.report.counts.needsReview")
                + JsonPath.<Integer>read(run, "$.report.counts.ready")).isEqualTo(targets.size());

        // The proposed follow-up waits for a person.
        String tasks = mvc.perform(get("/api/v1/follow-up-tasks").param("appointmentId", first.id().toString())
                .with(as(reception, clinic))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID task = UUID.fromString(JsonPath.read(tasks, "$[0].id"));
        assertThat(JsonPath.<String>read(tasks, "$[0].status")).isEqualTo("AWAITING_APPROVAL");
        assertThat(JsonPath.<String>read(tasks, "$[0].runId")).isEqualTo(runId.toString());

        // Approve, deliver; readiness is re-checked but the reminder satisfies nothing.
        Timestamp before = jdbc.queryForObject("SELECT evaluated_at FROM readiness_result WHERE appointment_id = ?",
                Timestamp.class, first.id());
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/approve", task).with(as(reception, clinic)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/follow-up-tasks/{id}/delivered", task).with(as(reception, clinic)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT evaluated_at FROM readiness_result WHERE appointment_id = ?",
                Timestamp.class, first.id())).isAfter(before);
        String after = mvc.perform(get("/api/v1/appointments/{id}/readiness", first.id()).with(as(reception, clinic)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(after, "$.checks[?(@.requirement=='DOCUMENT')].status"))
                .containsExactly("MISSING");

        // Audit trail: who did what, the run id on the agent's proposal.
        List<Map<String, Object>> trail = jdbc.queryForList("""
                SELECT action, actor_user_id, change_summary->>'runId' AS run_id FROM audit_event
                WHERE entity_id = ? ORDER BY occurred_at""", task);
        assertThat(trail).extracting(e -> e.get("action")).containsExactly("FOLLOW_UP_TASK_CREATED",
                "FOLLOW_UP_TASK_APPROVED", "FOLLOW_UP_TASK_DELIVERED");
        assertThat(trail).extracting(e -> e.get("actor_user_id")).containsOnly(reception.id());
        assertThat(trail.get(0).get("run_id")).isEqualTo(runId.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_action WHERE run_id = ?", Integer.class, runId))
                .isEqualTo(targets.size() + 2);

        // The run itself is audited, and every change it made carries its id.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE run_id = ? AND action = 'AGENT_RUN_STARTED'", Integer.class,
                runId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE run_id = ? AND action = 'AGENT_RUN_FINISHED'", Integer.class,
                runId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE run_id = ? AND action = 'AGENT_TOOL_CALLED'", Integer.class,
                runId)).isEqualTo(targets.size() + 2);
        assertThat(jdbc.queryForObject(
                "SELECT run_id FROM audit_event WHERE entity_id = ? AND action = 'FOLLOW_UP_TASK_CREATED'",
                UUID.class, task)).isEqualTo(runId);
        assertThat(jdbc.queryForObject(
                "SELECT run_id FROM audit_event WHERE entity_id = ? AND action = 'FOLLOW_UP_TASK_APPROVED'",
                UUID.class, task)).as("a human's approval is not part of the run").isNull();
        String viaApi = mvc.perform(get("/api/v1/audit-events").param("runId", runId.toString())
                        .with(as(manager, clinic)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Integer>read(viaApi, "$.total")).isEqualTo(targets.size() + 2 + 2 + 1);
    }

    private String awaitFinished(UUID runId, DemoData.User user, UUID clinic) throws Exception {
        for (int i = 0; i < 150; i++) {
            String body = mvc.perform(get("/api/v1/agent-runs/{id}", runId).with(as(user, clinic)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String status = JsonPath.read(body, "$.status");
            if (!status.equals("QUEUED") && !status.equals("RUNNING")) {
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run did not finish in 15 s");
    }

    private static DemoData.User user(DemoData data, String role) {
        return data.users().stream().filter(u -> u.role().equals(role)).findFirst().orElseThrow();
    }

    private static RequestPostProcessor as(DemoData.User user, UUID clinic) {
        return jwt().jwt(j -> j.subject(user.id().toString()).claim("clinicId", clinic.toString())
                .claim("role", user.role()));
    }

    private static void actAs(DemoData.User user, UUID clinic) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(user.id().toString())
                .claim("clinicId", clinic.toString()).claim("role", user.role()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
