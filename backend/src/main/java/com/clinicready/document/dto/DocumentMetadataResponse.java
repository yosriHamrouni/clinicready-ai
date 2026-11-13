package com.clinicready.document.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.clinicready.document.DocumentMetadata;

/**
 * A document's fields by name. {@code updatedBy} (with the editor's display name) and
 * {@code updatedAt} say who last edited them and when; all are null while nobody has saved a field.
 */
public record DocumentMetadataResponse(UUID documentId, Map<String, String> fields, UUID updatedBy,
        String updatedByName, Instant updatedAt) {

    public static DocumentMetadataResponse empty(UUID documentId) {
        return new DocumentMetadataResponse(documentId, Map.of(), null, null, null);
    }

    public static DocumentMetadataResponse from(DocumentMetadata m, String editorName) {
        return new DocumentMetadataResponse(m.getDocumentId(), m.getFields(), m.getUpdatedBy(), editorName,
                m.getUpdatedAt());
    }
}
