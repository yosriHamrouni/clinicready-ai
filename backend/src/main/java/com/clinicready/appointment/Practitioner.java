package com.clinicready.appointment;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "practitioner")
public class Practitioner {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String specialty;

    @Column(nullable = false)
    private boolean active;

    protected Practitioner() {
    }

    public Practitioner(UUID clinicId, String fullName, String specialty) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.fullName = fullName;
        this.specialty = specialty;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public boolean isActive() {
        return active;
    }
}
