package com.clinicready.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.clinicready.document.Document;
import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentSource;
import com.clinicready.document.DocumentStatus;

/** Document metadata. The storage key is internal and never exposed. */
public record DocumentResponse(
        UUID id,
        UUID patientId,
        DocumentCategory category,
        DocumentSource source,
        DocumentStatus status,
        int version,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256,
        UUID uploadedBy,
        Instant uploadedAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getPatientId(), d.getCategory(), d.getSource(), d.getStatus(),
                d.getVersion(), d.getOriginalFilename(), d.getContentType(), d.getSizeBytes(), d.getSha256(),
                d.getUploadedBy(), d.getUploadedAt());
    }
}
