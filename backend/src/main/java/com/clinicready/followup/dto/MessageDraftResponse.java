package com.clinicready.followup.dto;

import java.time.Instant;
import java.util.UUID;

import com.clinicready.common.Language;
import com.clinicready.followup.MessageDraft;

/**
 * The message staff will send for a task. {@code templateId} null: built-in text; {@code editedBy} null: the
 * text is as generated.
 */
public record MessageDraftResponse(UUID taskId, Language language, String body, UUID templateId, UUID editedBy,
        Instant createdAt, Instant updatedAt) {

    public static MessageDraftResponse from(MessageDraft d) {
        return new MessageDraftResponse(d.getTaskId(), d.getLanguage(), d.getBody(), d.getTemplateId(),
                d.getEditedBy(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
