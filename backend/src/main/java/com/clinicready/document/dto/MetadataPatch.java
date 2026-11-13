package com.clinicready.document.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PATCH /documents/{id}/metadata}. Only the listed fields change; a null or blank
 * value clears the field.
 */
public record MetadataPatch(@NotNull Map<String, String> fields) {
}
