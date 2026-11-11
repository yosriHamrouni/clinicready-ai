package com.clinicready.document.dto;

import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentSource;

/**
 * What the client says about an upload. {@code declaredContentType} and {@code declaredSize} are
 * only checked for consistency; the stored values come from inspecting the bytes.
 */
public record NewDocument(
        String filename,
        String declaredContentType,
        long declaredSize,
        DocumentCategory category,
        DocumentSource source) {
}
