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

/** A manager's reminder text for one requirement code (or {@code DEFAULT}) in one language. */
@Entity
@Table(name = "reminder_template")
public class ReminderTemplate {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "requirement_code", nullable = false, updatable = false)
    private String requirementCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Language language;

    @Column(nullable = false)
    private String body;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReminderTemplate() {
    }

    ReminderTemplate(UUID clinicId, String requirementCode, Language language, String body, UUID updatedBy) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.requirementCode = requirementCode;
        this.language = language;
        update(body, updatedBy);
    }

    void update(String body, UUID updatedBy) {
        this.body = body;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public Language getLanguage() {
        return language;
    }

    public String getBody() {
        return body;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
