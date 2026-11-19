package com.clinicready.readiness.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentStatus;
import com.clinicready.readiness.CheckStatus;
import com.clinicready.readiness.ReadinessEngine.Kind;

/**
 * One requirement of the appointment's checklist, with what supports its status. Document references are
 * what the system read; confirmations are administrative decisions recorded by a named staff member.
 *
 * @param ruleId           the configured requirement rule; null for built-in requirements (examinations, advance)
 * @param applicable       false when the status is {@code NOT_APPLICABLE}
 * @param remainingAction  what staff should do next; null when nothing is left to do
 */
public record ReadinessCheckResponse(
        UUID ruleId,
        Kind requirement,
        List<DocumentCategory> documentCategories,
        boolean mandatory,
        boolean applicable,
        CheckStatus status,
        String reason,
        String remainingAction,
        boolean requiresHumanReview,
        List<DocumentReference> documents,
        List<HumanConfirmation> confirmations) {

    /** Stable code of the requirement: the document category of a rule (unique per type), else the kind. */
    public String requirementCode() {
        return documentCategories.size() == 1 ? documentCategories.get(0).name() : requirement.name();
    }

    /** A stored document the status is based on. Read it through {@code GET /documents/{documentId}}. */
    public record DocumentReference(UUID documentId, DocumentCategory category, int version, DocumentStatus status,
            Instant uploadedAt) {
    }

    /** An administrative decision by a staff member, never an AI observation. */
    public record HumanConfirmation(String type, UUID confirmedBy, String confirmedByName, Instant confirmedAt) {
    }
}
