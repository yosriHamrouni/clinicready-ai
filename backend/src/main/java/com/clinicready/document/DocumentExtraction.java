package com.clinicready.document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What a model observed about one document: category, date and issuer, with its confidence and name.
 * The {@code documentId} is the reference back to the source document.
 *
 * <p>An observation, not a decision: it does not say a requirement is met, the document is authentic
 * or anything is covered or paid. Null values mean "could not be identified". Immutable once recorded;
 * staff corrections go to {@link DocumentMetadata}.
 */
@Entity
@Table(name = "document_extraction")
public class DocumentExtraction {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AiExtractionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private AiExtractionFailure failure;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private DocumentCategory category;

    @Column(name = "document_date", updatable = false)
    private LocalDate documentDate;

    @Column(updatable = false)
    private String issuer;

    @Column(updatable = false)
    private Double confidence;

    @Column(name = "model_name", nullable = false, updatable = false)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", updatable = false)
    private ReviewReason reviewReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentExtraction() {
    }

    private DocumentExtraction(UUID documentId, UUID clinicId, AiExtractionStatus status,
            AiExtractionFailure failure, DocumentCategory category, LocalDate documentDate, String issuer,
            Double confidence, String modelName, ReviewReason reviewReason) {
        this.documentId = documentId;
        this.clinicId = clinicId;
        this.status = status;
        this.failure = failure;
        this.category = category;
        this.documentDate = documentDate;
        this.issuer = issuer;
        this.confidence = confidence;
        this.modelName = modelName;
        this.reviewReason = reviewReason;
        this.createdAt = Instant.now();
    }

    static DocumentExtraction succeeded(UUID documentId, UUID clinicId, DocumentCategory category,
            LocalDate documentDate, String issuer, double confidence, String modelName, ReviewReason reviewReason) {
        return new DocumentExtraction(documentId, clinicId, AiExtractionStatus.SUCCEEDED, null, category,
                documentDate, issuer, confidence, modelName, reviewReason);
    }

    static DocumentExtraction failed(UUID documentId, UUID clinicId, AiExtractionFailure failure,
            String modelName) {
        return new DocumentExtraction(documentId, clinicId, AiExtractionStatus.FAILED, failure, null, null, null,
                null, modelName, ReviewReason.EXTRACTION_FAILED);
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public AiExtractionStatus getStatus() {
        return status;
    }

    public AiExtractionFailure getFailure() {
        return failure;
    }

    /** Null if the model could not identify it (or the extraction failed). */
    public DocumentCategory getCategory() {
        return category;
    }

    /** Null if no date could be identified. */
    public LocalDate getDocumentDate() {
        return documentDate;
    }

    /** Null if no issuer could be identified. */
    public String getIssuer() {
        return issuer;
    }

    /** The model's own confidence in 0..1; null when the extraction failed. */
    public Double getConfidence() {
        return confidence;
    }

    public String getModelName() {
        return modelName;
    }

    /** Null when no human review is needed. */
    public ReviewReason getReviewReason() {
        return reviewReason;
    }

    public boolean needsReview() {
        return reviewReason != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
