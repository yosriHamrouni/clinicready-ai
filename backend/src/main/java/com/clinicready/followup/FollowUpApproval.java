package com.clinicready.followup;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A human decision on a follow-up task: who decided, what, why and when. Immutable. */
@Entity
@Table(name = "follow_up_approval")
public class FollowUpApproval {

    public enum Decision {
        APPROVED,
        REJECTED
    }

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Decision decision;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(updatable = false)
    private String comment;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected FollowUpApproval() {
    }

    FollowUpApproval(FollowUpTask task, Decision decision, UUID actorId, String comment) {
        this.id = UUID.randomUUID();
        this.clinicId = task.getClinicId();
        this.taskId = task.getId();
        this.decision = decision;
        this.actorId = actorId;
        this.comment = comment;
        this.decidedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Decision getDecision() {
        return decision;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getComment() {
        return comment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
