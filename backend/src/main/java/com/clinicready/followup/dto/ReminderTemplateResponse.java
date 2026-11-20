package com.clinicready.followup.dto;

import java.time.Instant;
import java.util.UUID;

import com.clinicready.common.Language;
import com.clinicready.followup.ReminderTemplate;

public record ReminderTemplateResponse(UUID id, String requirementCode, Language language, String body,
        UUID updatedBy, Instant updatedAt) {

    public static ReminderTemplateResponse from(ReminderTemplate t) {
        return new ReminderTemplateResponse(t.getId(), t.getRequirementCode(), t.getLanguage(), t.getBody(),
                t.getUpdatedBy(), t.getUpdatedAt());
    }
}
