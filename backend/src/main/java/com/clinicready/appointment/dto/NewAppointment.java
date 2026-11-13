package com.clinicready.appointment.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /appointments}. Checklist data is set afterwards with {@code PUT …/admin-details}. */
public record NewAppointment(
        @NotNull UUID patientId,
        @NotNull UUID practitionerId,
        @NotNull UUID appointmentTypeId,
        @NotNull Instant startsAt) {
}
