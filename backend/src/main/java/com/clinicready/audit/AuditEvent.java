package com.clinicready.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One recorded state change. Append-only: every column is insert-only. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    /** Null when the system made the change. */
    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    /** Ids and field values only; never document text or patient identity details. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_summary", nullable = false, updatable = false)
    private Map<String, Object> changeSummary;

    @Column(name = "run_id", updatable = false)
    private UUID runId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    AuditEvent(UUID clinicId, UUID actorUserId, AuditAction action, String entityType, UUID entityId,
            Map<String, Object> changeSummary) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.changeSummary = changeSummary;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Map<String, Object> getChangeSummary() {
        return changeSummary;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
