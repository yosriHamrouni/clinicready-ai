package com.clinicready.patient;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.common.NotFoundException;
import com.clinicready.patient.dto.NewPatient;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Patients of the current user's clinic; a practitioner sees only patients they have an appointment
 * with. Other features use this service, never the repository.
 */
@Service
public class PatientService {

    private final PatientRepository patients;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;

    PatientService(PatientRepository patients, CurrentUserService currentUser, AuthorizationService authorization) {
        this.patients = patients;
        this.currentUser = currentUser;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<Patient> list() {
        authorization.require(Permission.PATIENT_READ);
        UUID clinicId = currentUser.clinicId();
        if (!isPractitioner()) {
            return patients.findAllByClinicIdOrderByPatientRef(clinicId);
        }
        return currentUser.practitionerId()
                .map(own -> patients.findAllOfPractitioner(clinicId, own))
                .orElse(List.of());
    }

    /** @throws NotFoundException if the patient is in another clinic or not visible to this user */
    @Transactional(readOnly = true)
    public Patient get(UUID id) {
        authorization.require(Permission.PATIENT_READ);
        UUID clinicId = currentUser.clinicId();
        return patients.findByIdAndClinicId(id, clinicId)
                .filter(patient -> visibleToCurrentUser(clinicId, patient))
                .orElseThrow(() -> new NotFoundException("Patient"));
    }

    @Transactional
    public Patient create(NewPatient patient) {
        authorization.require(Permission.PATIENT_WRITE);
        return patients.save(new Patient(currentUser.clinicId(), patient.patientRef(), patient.fullName(),
                patient.phone(), patient.preferredLanguage()));
    }

    private boolean visibleToCurrentUser(UUID clinicId, Patient patient) {
        if (!isPractitioner()) {
            return true;
        }
        return currentUser.practitionerId()
                .filter(own -> patients.hasAppointmentWith(clinicId, patient.getId(), own))
                .isPresent();
    }

    private boolean isPractitioner() {
        return currentUser.role() == Role.PRACTITIONER;
    }
}
