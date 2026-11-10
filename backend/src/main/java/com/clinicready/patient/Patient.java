package com.clinicready.patient;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.clinicready.common.Language;

/** A patient of one clinic. Synthetic data only. */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    private UUID id;

    @Column(name = "clinic_id", nullable = false, updatable = false)
    private UUID clinicId;

    /** Staff-facing reference such as P-0042, unique within the clinic. */
    @Column(name = "patient_ref", nullable = false)
    private String patientRef;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false)
    private Language preferredLanguage;

    protected Patient() {
    }

    public Patient(UUID clinicId, String patientRef, String fullName, String phone, Language preferredLanguage) {
        this.id = UUID.randomUUID();
        this.clinicId = clinicId;
        this.patientRef = patientRef;
        this.fullName = fullName;
        this.phone = phone;
        this.preferredLanguage = preferredLanguage;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClinicId() {
        return clinicId;
    }

    public String getPatientRef() {
        return patientRef;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Language getPreferredLanguage() {
        return preferredLanguage;
    }
}
