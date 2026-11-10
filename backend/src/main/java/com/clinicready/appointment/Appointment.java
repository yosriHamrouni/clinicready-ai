package com.clinicready.appointment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One patient seeing one practitioner for one appointment type. Related rows are referenced by id;
 * the database rejects a reference to a row of another clinic.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Column(name = "appointment_type_id", nullable = false)
    private UUID appointmentTypeId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    protected Appointment() {
    }

    public Appointment(UUID clinicId, UUID patientId, UUID practitionerId, UUID appointmentTypeId,
            Instant startsAt) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.appointmentTypeId = appointmentTypeId;
        this.startsAt = startsAt;
        this.status = AppointmentStatus.SCHEDULED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public UUID getPractitionerId() {
        return practitionerId;
    }

    public UUID getAppointmentTypeId() {
        return appointmentTypeId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public AppointmentStatus getStatus() {
        return status;
    }
}
