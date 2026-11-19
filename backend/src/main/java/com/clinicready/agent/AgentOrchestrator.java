package com.clinicready.agent;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import com.clinicready.common.ApiException;
import com.clinicready.readiness.AggregateStatus;
import com.clinicready.readiness.CheckStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The bounded agent loop. The model chooses tools; this class executes them itself (Spring AI's internal
 * tool execution is off), so every call is counted, timed and stored as an {@link AgentAction}, and the
 * limits hold: at most {@code maxToolCalls} calls and {@code timeout} of wall time, checked before every
 * model call and every tool call.
 *
 * <p>Runs on a worker thread whose security context is the initiator's, so tools see exactly what the
 * initiator may see. The final report must be JSON matching {@link AgentReport.Draft}; it is then
 * cross-checked against the stored readiness results, and any contradiction fails the run.
 */
@Component
class AgentOrchestrator {

    record Limits(int maxToolCalls, Duration timeout) {
    }

    private record Outcome(AgentRun.Status status, String reason, AgentReport report) {
    }

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    /** Tools that only read (checkReadiness recalculates, idempotently): retried once on failure. */
    static final Set<String> SAFE_READS = Set.of("getUpcomingAppointments", "getAppointmentRequirements",
            "listPatientDocuments", "inspectDocument", "checkReadiness", "getFollowUpStatus");

    /** Argument names a tool may receive; anything else is stored as "[redacted]". */
    private static final Set<String> LOGGABLE_ARGUMENTS = Set.of("appointmentId", "patientId", "documentId",
            "taskId", "approvalId", "appointmentTypeId", "practitionerId", "dateFrom", "dateTo", "requirementCode", "language",
            "fields");

    static final String SYSTEM_PROMPT = """
            You help clinic reception staff check whether upcoming appointments are administratively ready.
            Today is %s (clinic time zone Africa/Tunis).

            Use only the tools provided. Rules:
            - Readiness comes only from checkReadiness. Never decide a status yourself.
            - All mandatory checks PRESENT: report the appointment as READY, no follow-up.
            - A document exists but its type or date is uncertain: call inspectDocument; if still uncertain,
              leave it for human review.
            - A mandatory requirement MISSING or EXPIRED: call createFollowUpDraft (it returns the existing task
              if one is open). Never claim a message was sent or a payment confirmed.
            - Never give medical advice. READY means administratively ready only.

            When done, answer with JSON only, no prose around it:
            {"summary": "<two sentences>",
             "appointments": [{"appointmentId": "<uuid>", "status": "READY|NEEDS_REVIEW|INCOMPLETE",
               "findings": [{"requirementCode": "<code>", "status": "MISSING|EXPIRED|NEEDS_REVIEW",
                             "action": "<what staff should do>"}]}]}
            List every appointment you checked, with its status exactly as checkReadiness returned it, and
            one finding per mandatory requirement that is not PRESENT or NOT_APPLICABLE.""";

    private final AgentChat chat;
    private final AgentTools tools;
    private final ReadinessService readiness;
    private final AgentRunRepository runs;
    private final AgentActionRepository actions;
    private final ObjectMapper json;

    AgentOrchestrator(AgentChat chat, AgentTools tools, ReadinessService readiness, AgentRunRepository runs,
            AgentActionRepository actions, ObjectMapper json) {
        this.chat = chat;
        this.tools = tools;
        this.readiness = readiness;
        this.runs = runs;
        this.actions = actions;
        this.json = json.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** Runs a queued run to completion. Never throws: every failure ends the run with a status and reason. */
    void execute(UUID runId, Limits limits) {
        AgentRun run = runs.findById(runId).orElseThrow();
        run.start();
        runs.save(run); // merge: this instance stays the working copy
        Outcome outcome;
        AgentTools.CURRENT_RUN.set(runId);
        try {
            outcome = loop(run, limits, Instant.now().plus(limits.timeout()));
        } catch (RuntimeException e) {
            log.error("Agent run {} failed unexpectedly ({})", runId, e.getClass().getName(), e);
            outcome = new Outcome(AgentRun.Status.FAILED, "Unexpected error: " + e.getClass().getSimpleName(), null);
        } finally {
            AgentTools.CURRENT_RUN.remove();
        }
        run.finish(outcome.status(), outcome.reason(), outcome.report());
        runs.save(run);
        log.info("Agent run {} finished: {} toolCalls={} tokens={}", runId, outcome.status(), run.getToolCalls(),
                run.getPromptTokens() + run.getCompletionTokens());
    }

    private Outcome loop(AgentRun run, Limits limits, Instant deadline) {
        if (!chat.available()) {
            return new Outcome(AgentRun.Status.FAILED, "No model is configured", null);
        }
        Map<String, ToolCallback> byName = Arrays.stream(ToolCallbacks.from(tools))
                .collect(Collectors.toMap(c -> c.getToolDefinition().name(), Function.identity()));
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(List.copyOf(byName.values())).internalToolExecutionEnabled(false).build();
        List<Message> messages = new ArrayList<>(List.of(
                new SystemMessage(SYSTEM_PROMPT.formatted(LocalDate.now(ReadinessService.CLINIC_ZONE))),
                new UserMessage(run.getRequestText())));
        Map<UUID, AggregateStatus> checked = new LinkedHashMap<>();
        int seq = 0;

        while (true) {
            if (Instant.now().isAfter(deadline)) {
                return timedOut(limits);
            }
            ChatResponse response;
            try {
                response = chat.client().prompt(new Prompt(messages, options)).call().chatResponse();
            } catch (RuntimeException e) { // timeout, provider down, refused request
                log.warn("Agent run {}: model call failed ({})", run.getId(), e.getClass().getSimpleName());
                runs.save(run);
                return new Outcome(AgentRun.Status.FAILED,
                        "The model call failed (" + e.getClass().getSimpleName() + "); nothing was changed after it",
                        null);
            }
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int prompt = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int completion = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            run.addTokens(prompt, completion);
            AssistantMessage out = response.getResult().getOutput();
            messages.add(out);
            if (!out.hasToolCalls()) {
                runs.save(run);
                return report(out.getText(), checked);
            }

            int turnTokens = prompt + completion;
            List<ToolResponseMessage.ToolResponse> results = new ArrayList<>();
            for (AssistantMessage.ToolCall call : out.getToolCalls()) {
                ToolCallback callback = byName.get(call.name());
                // FR-D5: a safe read is tried once more; a write is never retried blindly.
                int attempts = callback != null && SAFE_READS.contains(call.name()) ? 2 : 1;
                Attempt attempt = null;
                for (int i = 0; i < attempts; i++) {
                    if (run.getToolCalls() >= limits.maxToolCalls()) {
                        runs.save(run);
                        return new Outcome(AgentRun.Status.TOOL_LIMIT_EXCEEDED,
                                "Stopped: the run asked for more than " + limits.maxToolCalls() + " tool calls", null);
                    }
                    if (Instant.now().isAfter(deadline)) {
                        runs.save(run);
                        return timedOut(limits);
                    }
                    attempt = invoke(callback, call);
                    run.countCall();
                    actions.save(new AgentAction(run, ++seq, call.name(), redact(call.arguments()), attempt.status(),
                            attempt.latencyMs(), turnTokens));
                    turnTokens = 0;
                    if (!attempt.failed()) {
                        break;
                    }
                }
                if (attempt.threw()) {
                    runs.save(run);
                    return new Outcome(AgentRun.Status.FAILED, "Stopped: tool " + call.name() + " failed"
                            + (attempts > 1 ? " twice (retried once)" : " and, as a write, was not retried"), null);
                }
                if ("checkReadiness".equals(call.name()) && "OK".equals(attempt.status())) {
                    remember(attempt.result(), checked);
                }
                results.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), attempt.result()));
            }
            runs.save(run); // merge: this instance stays the working copy
            messages.add(new ToolResponseMessage(results));
        }
    }

    /** One execution of a tool call. {@code threw}: the tool broke its contract by throwing. */
    private record Attempt(String result, String status, boolean threw, long latencyMs) {

        boolean failed() {
            return threw || "ERROR".equals(status);
        }
    }

    private Attempt invoke(ToolCallback callback, AssistantMessage.ToolCall call) {
        long start = System.nanoTime();
        if (callback == null) {
            return new Attempt("{\"status\":\"INVALID_INPUT\",\"message\":\"unknown tool\"}", "UNKNOWN_TOOL", false, 0);
        }
        try {
            String result = callback.call(call.arguments());
            return new Attempt(result, statusOf(result), false, elapsedMs(start));
        } catch (RuntimeException e) {
            log.warn("Tool {} threw {}", call.name(), e.getClass().getSimpleName());
            return new Attempt("{\"status\":\"ERROR\",\"message\":\"the tool call failed\"}", "EXCEPTION", true,
                    elapsedMs(start));
        }
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static Outcome timedOut(Limits limits) {
        return new Outcome(AgentRun.Status.TIMED_OUT, "Stopped: the run took longer than " + limits.timeout(), null);
    }

    /** Validates the model's final answer and cross-checks it against the stored readiness results. */
    private Outcome report(String text, Map<UUID, AggregateStatus> checked) {
        AgentReport.Draft draft;
        try {
            draft = json.readValue(stripFences(text), AgentReport.Draft.class);
        } catch (Exception e) {
            draft = null;
        }
        if (draft == null || !valid(draft)) {
            return new Outcome(AgentRun.Status.FAILED, "The final report is not valid JSON matching the report schema",
                    null);
        }
        AgentReport report = AgentReport.of(draft);
        String mismatch = crossCheck(report, checked);
        if (mismatch != null) {
            return new Outcome(AgentRun.Status.REPORT_MISMATCH, mismatch, report);
        }
        return new Outcome(AgentRun.Status.SUCCEEDED, null, report);
    }

    private String crossCheck(AgentReport report, Map<UUID, AggregateStatus> checked) {
        Set<UUID> reported = report.appointments().stream().map(AgentReport.AppointmentFinding::appointmentId)
                .collect(Collectors.toSet());
        for (UUID id : checked.keySet()) {
            if (!reported.contains(id)) {
                return "Appointment " + id + " was checked but is missing from the report";
            }
        }
        for (AgentReport.AppointmentFinding a : report.appointments()) {
            ReadinessResponse actual;
            try {
                actual = readiness.latest(a.appointmentId());
            } catch (ApiException e) {
                return "Appointment " + a.appointmentId() + " in the report has no readiness result";
            }
            if (actual.status() != a.status()) {
                return "Appointment " + a.appointmentId() + " is reported " + a.status() + " but readiness is "
                        + actual.status();
            }
            for (AgentReport.Finding f : a.findings()) {
                boolean matches = actual.checks().stream().anyMatch(
                        c -> c.requirementCode().equals(f.requirementCode()) && c.status() == f.status());
                if (!matches) {
                    return "Finding " + f.requirementCode() + " " + f.status() + " on appointment "
                            + a.appointmentId() + " does not match readiness";
                }
            }
            for (ReadinessCheckResponse c : actual.checks()) {
                boolean blocking = c.mandatory() && c.applicable() && c.status() != CheckStatus.PRESENT;
                if (blocking && a.findings().stream().noneMatch(f -> f.requirementCode().equals(c.requirementCode()))) {
                    return "Appointment " + a.appointmentId() + " is missing the finding " + c.requirementCode() + " "
                            + c.status();
                }
            }
        }
        return null;
    }

    private static boolean valid(AgentReport.Draft d) {
        return d.summary() != null && !d.summary().isBlank() && d.appointments() != null
                && d.appointments().stream().allMatch(a -> a != null && a.appointmentId() != null && a.status() != null
                        && a.findings() != null && a.findings().stream().allMatch(
                                f -> f != null && f.requirementCode() != null && f.status() != null));
    }

    private static String stripFences(String text) {
        String t = text == null ? "" : text.strip();
        if (t.startsWith("```")) {
            t = t.substring(t.indexOf('\n') + 1);
            int end = t.lastIndexOf("```");
            t = end >= 0 ? t.substring(0, end) : t;
        }
        return t.strip();
    }

    private String statusOf(String result) {
        try {
            JsonNode status = json.readTree(result).get("status");
            return status == null ? "ERROR" : status.asText();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private void remember(String result, Map<UUID, AggregateStatus> checked) {
        try {
            JsonNode data = json.readTree(result).get("data");
            checked.put(UUID.fromString(data.get("appointmentId").asText()),
                    AggregateStatus.valueOf(data.get("status").asText()));
        } catch (Exception e) {
            log.warn("Could not read a checkReadiness result ({})", e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redact(String arguments) {
        try {
            Map<String, Object> raw = json.readValue(arguments, Map.class);
            Map<String, Object> safe = new LinkedHashMap<>();
            raw.forEach((k, v) -> safe.put(k, LOGGABLE_ARGUMENTS.contains(k) ? v : "[redacted]"));
            return safe;
        } catch (Exception e) {
            return Map.of("_unparsed", "[redacted]");
        }
    }
}
