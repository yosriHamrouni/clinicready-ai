package com.clinicready.appointment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.clinicready.appointment.Appointment;
import com.clinicready.appointment.AppointmentStatus;
import com.clinicready.appointment.AppointmentView;
import com.clinicready.appointment.ContactMethod;

/**
 * An appointment with display data and its administrative checklist data. Each checklist item has
 * a status, so "not provided" ({@code MISSING}), "provided" and "confirmed by staff" stay distinct.
 */
public record AppointmentResponse(
        UUID id,
        Instant startsAt,
        AppointmentStatus status,
        Instant updatedAt,
        PatientSummary patient,
        PractitionerSummary practitioner,
        AppointmentTypeResponse appointmentType,
        AdminDetails adminDetails) {

    public static final String CURRENCY = "TND";

    public enum ItemStatus {
        MISSING,
        PROVIDED,
        /** Provided, and a staff member confirmed it (trusted contact reached, payment received). */
        CONFIRMED
    }

    public record PatientSummary(UUID id, String patientRef, String fullName) {
    }

    public record PractitionerSummary(UUID id, String fullName) {
    }

    public record Confirmation(UUID by, Instant at) {

        static Confirmation of(UUID by, Instant at) {
            return by == null ? null : new Confirmation(by, at);
        }
    }

    public record TrustedContact(ItemStatus status, String name, ContactMethod method, String value,
            Confirmation confirmation) {
    }

    public record ExpectedExaminations(ItemStatus status, List<String> items) {
    }

    /** The advance and whether its payment was confirmed. {@code status} is CONFIRMED once paid. */
    public record Advance(ItemStatus status, BigDecimal amount, String currency, Confirmation paymentConfirmation) {
    }

    public record AdminDetails(TrustedContact trustedContact, ExpectedExaminations expectedExaminations,
            Advance advance) {
    }

    public static AppointmentResponse from(AppointmentView view) {
        Appointment a = view.appointment();
        return new AppointmentResponse(a.getId(), a.getStartsAt(), a.getStatus(), a.getUpdatedAt(),
                new PatientSummary(view.patient().getId(), view.patient().getPatientRef(),
                        view.patient().getFullName()),
                new PractitionerSummary(view.practitioner().getId(), view.practitioner().getFullName()),
                AppointmentTypeResponse.from(view.type()),
                adminDetails(a));
    }

    private static AdminDetails adminDetails(Appointment a) {
        Confirmation contactConfirmation = Confirmation.of(a.getTrustedContactConfirmedBy(),
                a.getTrustedContactConfirmedAt());
        Confirmation paymentConfirmation = Confirmation.of(a.getPaymentConfirmedBy(), a.getPaymentConfirmedAt());
        return new AdminDetails(
                new TrustedContact(status(a.getTrustedContactName() != null, contactConfirmation),
                        a.getTrustedContactName(), a.getTrustedContactMethod(), a.getTrustedContactValue(),
                        contactConfirmation),
                new ExpectedExaminations(status(a.getExpectedExaminations() != null, null),
                        a.getExpectedExaminations()),
                new Advance(status(a.getAdvanceAmount() != null, paymentConfirmation), a.getAdvanceAmount(),
                        CURRENCY, paymentConfirmation));
    }

    private static ItemStatus status(boolean provided, Confirmation confirmation) {
        if (!provided) {
            return ItemStatus.MISSING;
        }
        return confirmation != null ? ItemStatus.CONFIRMED : ItemStatus.PROVIDED;
    }
}
