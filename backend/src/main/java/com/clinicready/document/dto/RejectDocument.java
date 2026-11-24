package com.clinicready.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /documents/{id}/reject}: why staff consider the document non-compliant. */
public record RejectDocument(@NotBlank @Size(max = 500) String reason) {
}
