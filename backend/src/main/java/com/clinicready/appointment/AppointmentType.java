package com.clinicready.appointment;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A kind of appointment, such as a consultation or an MRI. Requirement rules hang off it. */
@Entity
@Table(name = "appointment_type")
public class AppointmentType {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    /** Stable identifier, unique within the clinic. */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    protected AppointmentType() {
    }

    public AppointmentType(UUID clinicId, String code, String label) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.code = code;
        this.label = label;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
