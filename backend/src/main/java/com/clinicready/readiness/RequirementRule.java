package com.clinicready.readiness;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.clinicready.document.DocumentCategory;

/**
 * A document that appointments of one type need, configured by a clinic manager. The readiness
 * engine checks each appointment against the rules of its type.
 */
@Entity
@Table(name = "requirement_rule")
public class RequirementRule {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "appointment_type_id", nullable = false, updatable = false)
    private UUID appointmentTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_category", nullable = false)
    private DocumentCategory documentCategory;

    /** False: optional, reported but never blocks readiness. */
    @Column(nullable = false)
    private boolean mandatory;

    /** Days a document stays valid after upload; null = never expires. */
    @Column(name = "validity_days")
    private Integer validityDays;

    /** A matching document counts only once a staff member has verified it. */
    @Column(name = "needs_verification", nullable = false)
    private boolean needsVerification;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RequirementRule() {
    }

    RequirementRule(UUID clinicId, UUID appointmentTypeId, DocumentCategory documentCategory, boolean mandatory,
            Integer validityDays, boolean needsVerification) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.appointmentTypeId = appointmentTypeId;
        this.createdAt = Instant.now();
        update(documentCategory, mandatory, validityDays, needsVerification);
    }

    void update(DocumentCategory documentCategory, boolean mandatory, Integer validityDays,
            boolean needsVerification) {
        this.documentCategory = documentCategory;
        this.mandatory = mandatory;
        this.validityDays = validityDays;
        this.needsVerification = needsVerification;
        this.updatedAt = Instant.now();
    }

    /** The configurable fields, for audit events. */
    Map<String, Object> snapshot() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("documentCategory", documentCategory.name());
        fields.put("mandatory", mandatory);
        fields.put("validityDays", validityDays);
        fields.put("needsVerification", needsVerification);
        return fields;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getAppointmentTypeId() {
        return appointmentTypeId;
    }

    public DocumentCategory getDocumentCategory() {
        return documentCategory;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public boolean isNeedsVerification() {
        return needsVerification;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
