package com.clinicready.appointment;

import java.util.UUID;

/**
 * Published inside the transaction whenever an appointment is created or its checklist inputs
 * change (administrative data or a confirmation). The readiness engine listens to it to recalculate
 * the appointment's checklist.
 */
public record AppointmentChecklistChanged(UUID clinicId, UUID appointmentId) {
}
