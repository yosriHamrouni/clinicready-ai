package com.clinicready.followup.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.clinicready.common.Language;
import com.clinicready.followup.FollowUpApproval;
import com.clinicready.followup.FollowUpService.TaskView;
import com.clinicready.followup.FollowUpStatus;
import com.clinicready.followup.FollowUpTask;
import com.clinicready.readiness.CheckStatus;

/** A follow-up task. {@code approvals} is filled on single-task reads and empty in lists. */
public record FollowUpTaskResponse(
        UUID id,
        UUID appointmentId,
        UUID patientId,
        String requirementCode,
        FollowUpStatus status,
        CheckStatus checkStatus,
        String reason,
        String suggestedAction,
        List<UUID> evidenceDocumentIds,
        Language language,
        Instant dueAt,
        UUID createdBy,
        UUID runId,
        UUID approvalId,
        Instant deliveredAt,
        UUID deliveredBy,
        UUID deliveredByRun,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        List<Approval> approvals) {

    public record Approval(UUID id, FollowUpApproval.Decision decision, UUID actorId, String comment,
            Instant decidedAt) {
    }

    public static FollowUpTaskResponse from(TaskView view) {
        return from(view.task(), view.approvals());
    }

    public static FollowUpTaskResponse from(FollowUpTask t, List<FollowUpApproval> approvals) {
        return new FollowUpTaskResponse(t.getId(), t.getAppointmentId(), t.getPatientId(), t.getRequirementCode(),
                t.getStatus(), t.getCheckStatus(), t.getReason(), t.getSuggestedAction(), t.getEvidenceDocumentIds(),
                t.getLanguage(), t.getDueAt(), t.getCreatedBy(), t.getRunId(), t.getApprovalId(), t.getDeliveredAt(),
                t.getDeliveredBy(), t.getDeliveredByRun(), t.getResolvedAt(), t.getCreatedAt(), t.getUpdatedAt(),
                approvals.stream().map(a -> new Approval(a.getId(), a.getDecision(), a.getActorId(), a.getComment(),
                        a.getDecidedAt())).toList());
    }
}
