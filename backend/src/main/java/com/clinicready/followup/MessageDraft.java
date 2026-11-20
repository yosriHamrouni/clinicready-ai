package com.clinicready.followup;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.clinicready.common.Language;

/** The message staff will send for one follow-up task. Nothing sends it: staff copy it once approved. */
@Entity
@Table(name = "message_draft")
public class MessageDraft {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Language language;

    @Column(nullable = false)
    private String body;

    @Column(name = "template_id", updatable = false)
    private UUID templateId;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessageDraft() {
    }

    MessageDraft(FollowUpTask task, String body, UUID templateId) {
        this.id = UUID.randomUUID();
        this.clinicId = task.getClinicId();
        this.taskId = task.getId();
        this.language = task.getLanguage();
        this.body = body;
        this.templateId = templateId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void edit(String body, UUID editor) {
        this.body = body;
        this.editedBy = editor;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Language getLanguage() {
        return language;
    }

    public String getBody() {
        return body;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getEditedBy() {
        return editedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
