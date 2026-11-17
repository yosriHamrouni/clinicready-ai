package com.clinicready.readiness;

import java.util.List;
import java.util.UUID;

/**
 * The result of checking one requirement.
 *
 * @param evidenceDocumentIds the documents the status is based on; empty when none
 * @param reason human-readable explanation; for {@code MISSING} it names the sources checked
 */
public record ReadinessCheck(UUID ruleId, boolean mandatory, CheckStatus status, List<UUID> evidenceDocumentIds,
        String reason, boolean requiresHumanReview) {

    public ReadinessCheck {
        evidenceDocumentIds = List.copyOf(evidenceDocumentIds);
    }
}
