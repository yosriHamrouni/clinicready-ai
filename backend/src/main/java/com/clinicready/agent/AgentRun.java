package com.clinicready.agent;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One natural-language request to the agent, its outcome and its totals. Its tool calls are {@link AgentAction}s. */
@Entity
@Table(name = "agent_run")
public class AgentRun {

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        /** The model or a step failed, or the final report was not valid. */
        FAILED,
        TOOL_LIMIT_EXCEEDED,
        TIMED_OUT,
        /** The report contradicts the deterministic readiness results. */
        REPORT_MISMATCH;

        public boolean finished() {
            return this != QUEUED && this != RUNNING;
        }
    }

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "initiator_id", nullable = false, updatable = false)
    private UUID initiatorId;

    @Column(name = "request_text", nullable = false, updatable = false)
    private String requestText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "status_reason")
    private String statusReason;

    @JdbcTypeCode(SqlTypes.JSON)
    private AgentReport report;

    @Column(name = "tool_calls", nullable = false)
    private int toolCalls;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected AgentRun() {
    }

    AgentRun(UUID clinicId, UUID initiatorId, String requestText) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.initiatorId = initiatorId;
        this.requestText = requestText;
        this.status = Status.QUEUED;
        this.createdAt = Instant.now();
    }

    void start() {
        status = Status.RUNNING;
        startedAt = Instant.now();
    }

    void countCall() {
        toolCalls++;
    }

    void addTokens(int prompt, int completion) {
        promptTokens += prompt;
        completionTokens += completion;
    }

    void finish(Status status, String reason, AgentReport report) {
        this.status = status;
        this.statusReason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
        this.report = report;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getInitiatorId() {
        return initiatorId;
    }

    public String getRequestText() {
        return requestText;
    }

    public Status getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public AgentReport getReport() {
        return report;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
