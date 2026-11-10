package com.clinicready.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Every lookup takes the clinic id, so callers cannot forget the tenant filter. */
interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndClinicId(UUID id, UUID clinicId);

    Optional<Patient> findByClinicIdAndPatientRef(UUID clinicId, String patientRef);

    List<Patient> findAllByClinicIdOrderByPatientRef(UUID clinicId);
}
