package com.clinicready.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.clinicready.agent.ToolResult.ToolStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Runs the 40-case evaluation set (eval/dataset/eval-cases.json, YOS-25) through the whole backend: the
 * dataset is written to the database, readiness is computed by the real service, and the agent runs through
 * the real API with the <b>scripted stand-in model</b>. The script is built from the dataset's expected
 * outcome, so the model's judgement is NOT measured here: this measures the system around the model (the
 * readiness engine on stored data, the report cross-check, duplicate prevention, approval enforcement).
 * Extraction accuracy, real tool-use behaviour, cost and model latency need a real model run (see eval/README.md).
 *
 * <p>Results go to eval/results/stub-run.json when the environment variable {@code EVAL_WRITE_RESULTS} is set.
 */
@IntegrationTest
@ActiveProfiles(StubAiConfig.PROFILE)
class EvalRunIntegrationTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final Path DATASET = Path.of("..", "eval", "dataset", "eval-cases.json");
    private static final Path RESULTS = Path.of("..", "eval", "results", "stub-run.json");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ScriptedChatModel model;
    @Autowired
    private ReadinessService readiness;
    @Autowired
    private AgentTools tools;
    @Autowired
    private ObjectMapper json;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    private record Who(UUID id, UUID clinic) {
    }

    @Test
    void runsTheEvaluationSet() throws Exception {
        JsonNode root = json.readTree(Files.readString(DATASET));
        LocalDate day = LocalDate.now(TUNIS).plusDays(3);

        UUID clinic = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic (id, name) VALUES (?, 'Clinique Evaluation')", clinic);
        UUID receptionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_user (id, clinic_id, email, password_hash, full_name, role)
                VALUES (?, ?, ?, 'not-a-real-hash', 'Reception Eval', 'RECEPTIONIST')""", receptionId, clinic,
                receptionId + "@eval.test");
        Who reception = new Who(receptionId, clinic);
        Map<String, UUID> practitioners = new HashMap<>();
        for (String code : List.of("DR_BEN_SALEM", "DR_GHARBI")) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO practitioner (id, clinic_id, full_name) VALUES (?, ?, ?)", id, clinic, code);
            practitioners.put(code, id);
        }
        Map<String, UUID> types = new HashMap<>();
        root.get("requirementRules").fieldNames().forEachRemaining(code -> {
            UUID type = UUID.randomUUID();
            jdbc.update("INSERT INTO appointment_type (id, clinic_id, code, label) VALUES (?, ?, ?, ?)", type, clinic,
                    code, code);
            types.put(code, type);
            for (JsonNode rule : root.get("requirementRules").get(code)) {
                jdbc.update("""
                        INSERT INTO requirement_rule (id, clinic_id, appointment_type_id, document_category, mandatory,
                                                      validity_days, needs_verification)
                        VALUES (?, ?, ?, ?, ?, ?, FALSE)""", UUID.randomUUID(), clinic, type,
                        rule.get("category").asText(), rule.get("mandatory").asBoolean(),
                        rule.get("validityDays").isNull() ? null : rule.get("validityDays").asInt());
            }
        });

        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            results.add(runCase(c, root, day, reception, practitioners, types));
        }

        Map<String, Object> summary = summarize(results);
        if (System.getenv("EVAL_WRITE_RESULTS") != null) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("generatedAt", Instant.now().toString());
            out.put("model", ScriptedChatModel.MODEL_NAME);
            out.put("note", "Scripted stand-in model: the model's own judgement is not measured. See eval/report.md.");
            out.put("summary", summary);
            out.put("cases", results);
            Files.createDirectories(RESULTS.getParent());
            Files.writeString(RESULTS, json.writerWithDefaultPrettyPrinter().writeValueAsString(out) + "\n");
        }

        // The system around the model must be exact on this set.
        assertThat(summary.get("readinessAccuracy")).as(json.writeValueAsString(summary)).isEqualTo(1.0);
        assertThat(summary.get("reportFidelity")).isEqualTo(1.0);
        assertThat(summary.get("wrongReportsCaught")).isEqualTo(1.0);
        assertThat(summary.get("followUpCountAccuracy")).isEqualTo(1.0);
        assertThat(summary.get("duplicateTasks")).isEqualTo(0);
        assertThat(summary.get("approvalEnforcement")).isEqualTo(1.0);
        assertThat(summary.get("toolCallSuccessRate")).isEqualTo(1.0);
    }

    private Map<String, Object> runCase(JsonNode c, JsonNode root, LocalDate day, Who reception,
            Map<String, UUID> practitioners, Map<String, UUID> types) throws Exception {
        String id = c.get("id").asText();
        JsonNode expected = c.get("expected");
        UUID appointment = seed(c, day, reception.clinic(), reception.id(), practitioners, types);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("scenario", c.get("scenario").asText());
        r.put("appointmentType", c.get("appointmentType").asText());

        // 1. Readiness from stored data, by the real service.
        actAs(reception);
        ReadinessResponse actual = readiness.run(appointment);
        SecurityContextHolder.clearContext();
        boolean correct = actual.status().name().equals(expected.get("aggregate").asText());
        Map<String, String> statuses = new LinkedHashMap<>();
        for (ReadinessCheckResponse check : actual.checks()) {
            statuses.put(check.requirementCode(), check.status().name());
        }
        var wanted = expected.get("checks").fields();
        while (wanted.hasNext()) {
            var entry = wanted.next();
            correct &= entry.getValue().asText().equals(statuses.get(entry.getKey()));
        }
        r.put("readinessCorrect", correct);
        r.put("expectedAggregate", expected.get("aggregate").asText());
        r.put("actualAggregate", actual.status().name());

        // 2. The agent, twice (the second run must not duplicate what the first proposed).
        List<String> blocking = new ArrayList<>();
        List<Map<String, Object>> findings = new ArrayList<>();
        var checks = expected.get("checks").fields();
        while (checks.hasNext()) {
            var entry = checks.next();
            if (!isMandatory(root, c, entry.getKey()) || entry.getValue().asText().equals("PRESENT")) {
                continue;
            }
            findings.add(Map.of("requirementCode", entry.getKey(), "status", entry.getValue().asText(), "action",
                    "A traiter"));
            if (entry.getValue().asText().equals("MISSING") || entry.getValue().asText().equals("EXPIRED")) {
                blocking.add(entry.getKey());
            }
        }
        String aggregate = expected.get("aggregate").asText();
        String first = agent(reception, appointment, blocking, report(appointment, aggregate, findings), id);
        r.put("runStatus", JsonPath.read(first, "$.status"));
        r.put("toolCalls", JsonPath.<Integer>read(first, "$.toolCalls"));
        List<String> resultStatuses = JsonPath.read(first, "$.actions[*].resultStatus");
        r.put("toolCallsOk", resultStatuses.stream().filter("OK"::equals).count());
        r.put("latencyMs", Duration.between(Instant.parse(JsonPath.read(first, "$.startedAt")),
                Instant.parse(JsonPath.read(first, "$.finishedAt"))).toMillis());
        agent(reception, appointment, blocking, report(appointment, aggregate, findings), id + " again");

        int tasks = jdbc.queryForObject("SELECT count(*) FROM follow_up_task WHERE appointment_id = ?", Integer.class,
                appointment);
        int distinct = jdbc.queryForObject(
                "SELECT count(DISTINCT requirement_code) FROM follow_up_task WHERE appointment_id = ?", Integer.class,
                appointment);
        r.put("followUpsExpected", expected.get("followUpTasks").asInt());
        r.put("followUpsActual", tasks);
        r.put("duplicateTasks", tasks - distinct);

        // 3. A wrong report must be caught by the cross-check.
        String wrong = aggregate.equals("READY") ? "INCOMPLETE" : "READY";
        String tampered = agent(reception, appointment, List.of(), report(appointment, wrong, List.of()), id + " wrong");
        r.put("wrongReportCaught", "REPORT_MISMATCH".equals(JsonPath.read(tampered, "$.status")));

        // 4. Nothing runs without a person's approval.
        int attempts = 0;
        int blocked = 0;
        boolean authorizedWorked = true;
        if (tasks > 0) {
            UUID task = jdbc.queryForObject("SELECT id FROM follow_up_task WHERE appointment_id = ? LIMIT 1",
                    UUID.class, appointment);
            actAs(reception);
            attempts++;
            blocked += tools.submitApprovedAction(task.toString(), UUID.randomUUID().toString()).status()
                    == ToolStatus.CONFLICT ? 1 : 0;
            SecurityContextHolder.clearContext();
            String approved = mvc.perform(post("/api/v1/follow-up-tasks/{id}/approve", task).with(as(reception)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            UUID approval = UUID.fromString(JsonPath.read(approved, "$.approvalId"));
            actAs(reception);
            authorizedWorked = tools.submitApprovedAction(task.toString(), approval.toString()).status()
                    == ToolStatus.OK;
            attempts++;
            blocked += tools.submitApprovedAction(task.toString(), approval.toString()).status()
                    == ToolStatus.CONFLICT ? 1 : 0; // an approval is used once
            SecurityContextHolder.clearContext();
        }
        r.put("unapprovedAttempts", attempts);
        r.put("unapprovedBlocked", blocked);
        r.put("approvedActionWorked", authorizedWorked);
        return r;
    }

    private Map<String, Object> summarize(List<Map<String, Object>> results) {
        int n = results.size();
        long readinessOk = results.stream().filter(r -> (boolean) r.get("readinessCorrect")).count();
        long reportOk = results.stream().filter(r -> "SUCCEEDED".equals(r.get("runStatus"))).count();
        long caught = results.stream().filter(r -> (boolean) r.get("wrongReportCaught")).count();
        long followUpOk = results.stream().filter(r -> r.get("followUpsExpected").equals(r.get("followUpsActual")))
                .count();
        int duplicates = results.stream().mapToInt(r -> (int) r.get("duplicateTasks")).sum();
        int attempts = results.stream().mapToInt(r -> (int) r.get("unapprovedAttempts")).sum();
        int blocked = results.stream().mapToInt(r -> (int) r.get("unapprovedBlocked")).sum();
        long approvedOk = results.stream().filter(r -> (int) r.get("followUpsActual") > 0)
                .filter(r -> (boolean) r.get("approvedActionWorked")).count();
        int calls = results.stream().mapToInt(r -> (int) r.get("toolCalls")).sum();
        long callsOk = results.stream().mapToLong(r -> (long) r.get("toolCallsOk")).sum();
        List<Long> latencies = results.stream().map(r -> (long) r.get("latencyMs")).sorted(Comparator.naturalOrder())
                .toList();

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("cases", n);
        s.put("readinessAccuracy", (double) readinessOk / n);
        s.put("reportFidelity", (double) reportOk / n);
        s.put("wrongReportsCaught", (double) caught / n);
        s.put("followUpCountAccuracy", (double) followUpOk / n);
        s.put("duplicateTasks", duplicates);
        s.put("approvalEnforcement", attempts == 0 ? 1.0 : (double) blocked / attempts);
        s.put("unapprovedAttempts", attempts);
        s.put("approvedActionsThatWorked", approvedOk);
        s.put("toolCalls", calls);
        s.put("toolCallSuccessRate", calls == 0 ? 1.0 : (double) callsOk / calls);
        s.put("pipelineLatencyMsMedian", latencies.get(n / 2));
        s.put("pipelineLatencyMsMax", latencies.get(n - 1));
        return s;
    }

    private boolean isMandatory(JsonNode root, JsonNode c, String category) {
        for (JsonNode rule : root.get("requirementRules").get(c.get("appointmentType").asText())) {
            if (rule.get("category").asText().equals(category)) {
                return rule.get("mandatory").asBoolean();
            }
        }
        return false;
    }

    private String report(UUID appointment, String aggregate, List<Map<String, Object>> findings)
            throws Exception {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("appointmentId", appointment.toString());
        entry.put("status", aggregate);
        entry.put("findings", findings);
        return json.writeValueAsString(Map.of("summary", "Evaluation case checked.", "appointments", List.of(entry)));
    }

    /** One agent run with the scripted model: check readiness, propose a follow-up per blocking item, report. */
    private String agent(Who who, UUID appointment, List<String> blocking, String reportJson, String label)
            throws Exception {
        List<ScriptedChatModel.Step> script = new ArrayList<>();
        script.add(ScriptedChatModel.toolCall("checkReadiness", "{\"appointmentId\":\"" + appointment + "\"}"));
        for (String code : blocking) {
            script.add(ScriptedChatModel.toolCall("createFollowUpDraft",
                    "{\"appointmentId\":\"%s\",\"requirementCode\":\"%s\"}".formatted(appointment, code)));
        }
        script.add(ScriptedChatModel.text(reportJson));
        model.reset();
        model.script(script.toArray(ScriptedChatModel.Step[]::new));
        String started = mvc.perform(post("/api/v1/agent-runs").with(as(who)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Evaluation " + label + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID runId = UUID.fromString(JsonPath.read(started, "$.runId"));
        for (int i = 0; i < 300; i++) {
            String body = mvc.perform(get("/api/v1/agent-runs/{id}", runId).with(as(who)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(body, "$.status");
            if (!state.equals("QUEUED") && !state.equals("RUNNING")) {
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("run did not finish in 30 s: " + label);
    }

    /** Writes one case: a patient, an appointment with no admin requirements outstanding, and its documents. */
    private UUID seed(JsonNode c, LocalDate day, UUID clinic, UUID uploader, Map<String, UUID> practitioners,
            Map<String, UUID> types) {
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patient (id, clinic_id, patient_ref, full_name) VALUES (?, ?, ?, 'Patient Evaluation')",
                patient, clinic, c.get("id").asText() + "-" + patient.toString().substring(0, 6));
        UUID appointment = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO appointment (id, clinic_id, patient_id, practitioner_id, appointment_type_id, starts_at,
                    expected_examinations, advance_amount)
                VALUES (?, ?, ?, ?, ?, ?, '[]'::jsonb, 0)""", appointment, clinic, patient,
                practitioners.get(c.get("practitioner").asText()), types.get(c.get("appointmentType").asText()),
                Timestamp.from(day.atTime(9, 0).atZone(TUNIS).toInstant()));
        for (JsonNode d : c.get("documents")) {
            UUID doc = UUID.randomUUID();
            boolean failed = d.get("extractionStatus").asText().equals("EXTRACTION_FAILED");
            Timestamp uploaded = Timestamp.from(day.minusDays(d.get("uploadedDaysBeforeAppointment").asInt())
                    .atTime(12, 0).atZone(TUNIS).toInstant());
            jdbc.update("""
                    INSERT INTO document (id, clinic_id, patient_id, category, source, status, version,
                        original_filename, content_type, size_bytes, sha256, storage_key, uploaded_by, uploaded_at,
                        extraction_status, extraction_failure, extracted_at)
                    VALUES (?, ?, ?, ?, 'STAFF_UPLOAD', ?, 1, 'eval.pdf', 'application/pdf', 10, ?, ?, ?, ?, ?, ?, now())""",
                    doc, clinic, patient, d.get("category").asText(), d.get("status").asText(), "e".repeat(64),
                    clinic + "/" + doc, uploader, uploaded, failed ? "EXTRACTION_FAILED" : "EXTRACTED",
                    failed ? "UNREADABLE" : null);
            if (!failed) {
                String reason = d.get("reviewReason").isNull() ? null : d.get("reviewReason").asText();
                jdbc.update("""
                        INSERT INTO document_extraction (document_id, clinic_id, status, category, confidence,
                            model_name, review_reason)
                        VALUES (?, ?, 'SUCCEEDED', ?, ?, 'eval-fixture', ?)""", doc, clinic, d.get("category").asText(),
                        reason == null ? 0.95 : 0.4, reason);
            }
            // Dates come from staff-corrected metadata: an expiry, or a document date after the expiry (a conflict).
            String fields = null;
            if (d.get("datesConflict").asBoolean()) {
                fields = "{\"documentDate\":\"%s\",\"expiryDate\":\"%s\"}".formatted(day.minusDays(1), day.minusDays(30));
            } else if (!d.get("expiryDaysFromAppointment").isNull()) {
                fields = "{\"expiryDate\":\"%s\"}".formatted(day.plusDays(d.get("expiryDaysFromAppointment").asInt()));
            }
            if (fields != null) {
                jdbc.update("""
                        INSERT INTO document_metadata (document_id, clinic_id, fields, updated_by)
                        VALUES (?, ?, ?::jsonb, ?)""", doc, clinic, fields, uploader);
            }
        }
        return appointment;
    }

    private static RequestPostProcessor as(Who who) {
        return jwt().jwt(j -> j.subject(who.id().toString()).claim("clinicId", who.clinic().toString())
                .claim("role", "RECEPTIONIST"));
    }

    private static void actAs(Who who) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(who.id().toString())
                .claim("clinicId", who.clinic().toString()).claim("role", "RECEPTIONIST").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
