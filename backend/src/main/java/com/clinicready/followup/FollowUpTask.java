package com.clinicready.followup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.clinicready.common.Language;
import com.clinicready.readiness.CheckStatus;

/**
 * What staff should do about one requirement of one appointment. Holds ids, the requirement code and the
 * readiness reason only. Status changes go through {@link FollowUpService}.
 */
@Entity
@Table(name = "follow_up_task")
public class FollowUpTask {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "requirement_code", nullable = false, updatable = false)
    private String requirementCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FollowUpStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_status", nullable = false, updatable = false)
    private CheckStatus checkStatus;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "suggested_action", updatable = false)
    private String suggestedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_document_ids", nullable = false, updatable = false)
    private List<UUID> evidenceDocumentIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "run_id", updatable = false)
    private UUID runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FollowUpTask() {
    }

    FollowUpTask(UUID clinicId, UUID appointmentId, UUID patientId, String requirementCode, CheckStatus checkStatus,
            String reason, String suggestedAction, List<UUID> evidenceDocumentIds, Language language, Instant dueAt,
            UUID createdBy, UUID runId) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.requirementCode = requirementCode;
        this.status = FollowUpStatus.AWAITING_APPROVAL;
        this.checkStatus = checkStatus;
        this.reason = reason;
        this.suggestedAction = suggestedAction;
        this.evidenceDocumentIds = List.copyOf(evidenceDocumentIds);
        this.language = language;
        this.dueAt = dueAt;
        this.createdBy = createdBy;
        this.runId = runId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void moveTo(FollowUpStatus next) {
        status = next;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public FollowUpStatus getStatus() {
        return status;
    }

    public CheckStatus getCheckStatus() {
        return checkStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public List<UUID> getEvidenceDocumentIds() {
        return evidenceDocumentIds;
    }

    public Language getLanguage() {
        return language;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getRunId() {
        return runId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
