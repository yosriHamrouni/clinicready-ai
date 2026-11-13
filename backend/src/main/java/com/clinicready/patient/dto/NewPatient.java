package com.clinicready.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.clinicready.common.Language;
import com.clinicready.common.PhoneNumbers;

/**
 * Body of {@code POST /patients}. {@code patientRef} is generated when omitted; {@code phone} is
 * optional and only checked for format; {@code preferredLanguage} defaults to FR.
 */
public record NewPatient(
        @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$") String patientRef,
        @NotBlank @Size(max = 200) String fullName,
        @Pattern(regexp = PhoneNumbers.REGEX) String phone,
        Language preferredLanguage) {
}
