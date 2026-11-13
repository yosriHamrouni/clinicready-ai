package com.clinicready.document;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Staff-corrected fields of one document, with who last edited them and when. */
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    /** Field key (see {@link DocumentMetadataField#key()}) to value. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> fields;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentMetadata() {
    }

    DocumentMetadata(UUID documentId, UUID clinicId, Map<String, String> fields, UUID updatedBy) {
        this.documentId = documentId;
        this.clinicId = clinicId;
        this.fields = new TreeMap<>(fields);
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    void replaceFields(Map<String, String> fields, UUID updatedBy) {
        this.fields = new TreeMap<>(fields);
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public Map<String, String> getFields() {
        return Map.copyOf(fields);
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
