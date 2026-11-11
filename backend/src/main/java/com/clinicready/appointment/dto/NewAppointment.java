package com.clinicready.appointment.dto;

import java.time.Instant;
import java.util.UUID;

public record NewAppointment(UUID patientId, UUID practitionerId, UUID appointmentTypeId, Instant startsAt) {
}
