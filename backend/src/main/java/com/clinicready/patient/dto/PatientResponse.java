package com.clinicready.patient.dto;

import java.util.UUID;

import com.clinicready.common.Language;
import com.clinicready.patient.Patient;

public record PatientResponse(UUID id, String patientRef, String fullName, String phone,
        Language preferredLanguage) {

    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.getId(), p.getPatientRef(), p.getFullName(), p.getPhone(),
                p.getPreferredLanguage());
    }
}
