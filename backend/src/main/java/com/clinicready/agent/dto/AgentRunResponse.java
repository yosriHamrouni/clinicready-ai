package com.clinicready.agent.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinicready.agent.AgentAction;
import com.clinicready.agent.AgentReport;
import com.clinicready.agent.AgentRun;
import com.clinicready.agent.AgentRunService.RunView;

/** A run with its outcome, totals and every tool call in order. */
public record AgentRunResponse(
        UUID id,
        String request,
        AgentRun.Status status,
        String statusReason,
        AgentReport report,
        int toolCalls,
        int promptTokens,
        int completionTokens,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        List<Action> actions) {

    public record Started(UUID runId, AgentRun.Status status) {
    }

    public record Action(int seq, String tool, Map<String, Object> arguments, String resultStatus, long latencyMs,
            int tokens, Instant at) {

        static Action from(AgentAction a) {
            return new Action(a.getSeq(), a.getTool(), a.getArguments(), a.getResultStatus(), a.getLatencyMs(),
                    a.getTokens(), a.getCreatedAt());
        }
    }

    public static AgentRunResponse from(RunView view) {
        AgentRun r = view.run();
        return new AgentRunResponse(r.getId(), r.getRequestText(), r.getStatus(), r.getStatusReason(), r.getReport(),
                r.getToolCalls(), r.getPromptTokens(), r.getCompletionTokens(), r.getCreatedAt(), r.getStartedAt(),
                r.getFinishedAt(), view.actions().stream().map(Action::from).toList());
    }
}
