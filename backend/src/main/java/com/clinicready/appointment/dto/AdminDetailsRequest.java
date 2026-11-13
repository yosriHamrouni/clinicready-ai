package com.clinicready.appointment.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.clinicready.appointment.ContactMethod;

/**
 * Body of {@code PUT /appointments/{id}/admin-details}. Replaces every administrative field; a null
 * field means "not provided". Confirmations are not part of it: they have their own endpoints.
 *
 * @param expectedExaminations null when not entered yet; an empty list when none are expected
 * @param advanceAmount        in Tunisian dinars, typed by staff
 */
public record AdminDetailsRequest(
        @Valid TrustedContact trustedContact,
        @Size(max = 30) List<@NotBlank @Size(max = 200) String> expectedExaminations,
        @DecimalMin("0") @Digits(integer = 9, fraction = 3) BigDecimal advanceAmount) {

    /** {@code value} must be a well-formed phone number or e-mail address, depending on {@code method}. */
    public record TrustedContact(
            @NotBlank @Size(max = 200) String name,
            @NotNull ContactMethod method,
            @NotBlank @Size(max = 254) String value) {
    }
}
