package com.clinicready.document;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Every user-facing lookup takes the clinic id, so callers cannot forget the tenant filter. The
 * extraction methods are the exception: they run as a background system job, not for a user, and
 * are used only by {@link DocumentTextExtraction}.
 */
interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndClinicId(UUID id, UUID clinicId);

    List<Document> findAllByClinicIdAndPatientIdOrderByCategoryAscVersionDesc(UUID clinicId, UUID patientId);

    @Query("""
            SELECT COALESCE(MAX(d.version), 0) FROM Document d
            WHERE d.clinicId = :clinicId AND d.patientId = :patientId AND d.category = :category""")
    int maxVersion(UUID clinicId, UUID patientId, DocumentCategory category);

    /** Ids of documents still waiting for extraction, uploaded before {@code before}, oldest first. */
    @Query("""
            SELECT d.id FROM Document d
            WHERE d.extractionStatus = com.clinicready.document.ExtractionStatus.PENDING
              AND d.uploadedAt < :before
            ORDER BY d.uploadedAt""")
    List<UUID> findPendingExtraction(Instant before, Limit limit);

    /**
     * Marks the document {@code EXTRACTED} if it is still pending. Returns 0 if another worker already
     * finished it, in which case the caller must not store text.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE document SET extraction_status = 'EXTRACTED', extracted_at = :at
            WHERE id = :id AND extraction_status = 'PENDING'""")
    int markExtracted(UUID id, Instant at);

    /** Marks the document {@code EXTRACTION_FAILED} if it is still pending. Returns 0 otherwise. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE document SET extraction_status = 'EXTRACTION_FAILED', extraction_failure = :failure,
                                extracted_at = :at
            WHERE id = :id AND extraction_status = 'PENDING'""")
    int markExtractionFailed(UUID id, String failure, Instant at);
}
