package com.clinicready.agent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One tool call of an {@link AgentRun}: which tool, redacted arguments, result status, latency, tokens. */
@Entity
@Table(name = "agent_action")
public class AgentAction {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(nullable = false, updatable = false)
    private int seq;

    @Column(nullable = false, updatable = false)
    private String tool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, Object> arguments;

    @Column(name = "result_status", nullable = false, updatable = false)
    private String resultStatus;

    @Column(name = "latency_ms", nullable = false, updatable = false)
    private long latencyMs;

    @Column(nullable = false, updatable = false)
    private int tokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentAction() {
    }

    AgentAction(AgentRun run, int seq, String tool, Map<String, Object> arguments, String resultStatus,
            long latencyMs, int tokens) {
        this.id = UUID.randomUUID();
        this.clinicId = run.getClinicId();
        this.runId = run.getId();
        this.seq = seq;
        this.tool = tool;
        this.arguments = arguments;
        this.resultStatus = resultStatus;
        this.latencyMs = latencyMs;
        this.tokens = tokens;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public int getSeq() {
        return seq;
    }

    public String getTool() {
        return tool;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getTokens() {
        return tokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
