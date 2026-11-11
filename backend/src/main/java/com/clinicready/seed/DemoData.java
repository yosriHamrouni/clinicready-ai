package com.clinicready.seed;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Plain, framework-free description of the fictional demo clinic. Produced by
 * {@link DemoDataGenerator}; written to the database by {@link DemoDataSeeder}.
 *
 * <p>Everything here is synthetic: invented names, reserved {@code .test} e-mail domain and
 * deliberately unassignable phone numbers.
 */
public record DemoData(
        Clinic clinic,
        List<Practitioner> practitioners,
        List<User> users,
        List<AppointmentType> appointmentTypes,
        List<Patient> patients,
        List<Appointment> appointments) {

    public record Clinic(UUID id, String name, String city) {}

    public record Practitioner(UUID id, String fullName, String specialty) {}

    /** {@code role} is the name of {@code com.clinicready.security.Role}. */
    public record User(UUID id, String email, String fullName, String role, UUID practitionerId) {}

    public record AppointmentType(UUID id, String code, String label) {}

    /** {@code language} is {@code FR} or {@code AR}; Arabic-language patients have Arabic-script names. */
    public record Patient(UUID id, String ref, String fullName, String phone, String language) {}

    /** {@code status} is the name of {@code com.clinicready.appointment.AppointmentStatus}. */
    public record Appointment(
            UUID id,
            UUID patientId,
            UUID practitionerId,
            UUID appointmentTypeId,
            OffsetDateTime startsAt,
            String status) {}
}
