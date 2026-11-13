package com.clinicready.appointment;

import com.clinicready.patient.Patient;

/** An appointment with the rows it references, for display. */
public record AppointmentView(Appointment appointment, Patient patient, Practitioner practitioner,
        AppointmentType type) {
}
