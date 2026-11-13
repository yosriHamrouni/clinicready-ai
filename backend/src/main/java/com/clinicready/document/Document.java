package com.clinicready.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Metadata of one stored file version. The bytes live in {@link DocumentStorage}. */
@Entity
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DocumentSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    // Written only by DocumentRepository#markExtracted / #markExtractionFailed, never through the entity.
    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, insertable = false, updatable = false)
    private ExtractionStatus extractionStatus = ExtractionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_failure", insertable = false, updatable = false)
    private ExtractionFailure extractionFailure;

    @Column(name = "extracted_at", insertable = false, updatable = false)
    private Instant extractedAt;

    protected Document() {
    }

    Document(UUID id, UUID clinicId, UUID patientId, DocumentCategory category, DocumentSource source,
            int version, String originalFilename, String contentType, long sizeBytes, String sha256,
            String storageKey, UUID uploadedBy) {
        this.id = id;
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.category = category;
        this.source = source;
        this.status = DocumentStatus.UPLOADED;
        this.version = version;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public DocumentSource getSource() {
        return source;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    String getStorageKey() {
        return storageKey;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public ExtractionStatus getExtractionStatus() {
        return extractionStatus;
    }

    /** Null unless the status is {@link ExtractionStatus#EXTRACTION_FAILED}. */
    public ExtractionFailure getExtractionFailure() {
        return extractionFailure;
    }

    /** When extraction finished (succeeded or failed); null while pending. */
    public Instant getExtractedAt() {
        return extractedAt;
    }
}
