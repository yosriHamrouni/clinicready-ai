package com.clinicready.readiness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.clinicready.document.DocumentCategory;

/** Every field of a rule; PUT replaces them all. A null {@code validityDays} means no expiry. */
public record RequirementRuleRequest(
        @NotNull DocumentCategory documentCategory,
        @NotNull Boolean mandatory,
        @Min(1) @Max(3650) Integer validityDays,
        @NotNull Boolean needsVerification) {
}
