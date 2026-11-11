package com.clinicready.patient.dto;

import com.clinicready.common.Language;

public record NewPatient(String patientRef, String fullName, String phone, Language preferredLanguage) {
}
