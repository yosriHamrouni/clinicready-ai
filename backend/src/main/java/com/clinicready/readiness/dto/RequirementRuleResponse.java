package com.clinicready.readiness.dto;

import java.time.Instant;
import java.util.UUID;

import com.clinicready.document.DocumentCategory;
import com.clinicready.readiness.RequirementRule;

public record RequirementRuleResponse(
        UUID id,
        UUID appointmentTypeId,
        DocumentCategory documentCategory,
        boolean mandatory,
        Integer validityDays,
        boolean needsVerification,
        Instant updatedAt) {

    public static RequirementRuleResponse from(RequirementRule r) {
        return new RequirementRuleResponse(r.getId(), r.getAppointmentTypeId(), r.getDocumentCategory(),
                r.isMandatory(), r.getValidityDays(), r.isNeedsVerification(), r.getUpdatedAt());
    }
}
