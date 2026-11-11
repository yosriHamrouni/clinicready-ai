package com.clinicready.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every lookup takes the clinic id, so callers cannot forget the tenant filter. */
interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndClinicId(UUID id, UUID clinicId);

    Optional<Patient> findByClinicIdAndPatientRef(UUID clinicId, String patientRef);

    List<Patient> findAllByClinicIdOrderByPatientRef(UUID clinicId);

    /** Patients with at least one appointment with the practitioner. */
    @Query(value = """
            SELECT p.* FROM patient p
            WHERE p.clinic_id = :clinicId AND EXISTS (
                SELECT 1 FROM appointment a
                WHERE a.patient_id = p.id AND a.clinic_id = :clinicId AND a.practitioner_id = :practitionerId)
            ORDER BY p.patient_ref
            """, nativeQuery = true)
    List<Patient> findAllOfPractitioner(UUID clinicId, UUID practitionerId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM appointment a
                WHERE a.patient_id = :patientId AND a.clinic_id = :clinicId AND a.practitioner_id = :practitionerId)
            """, nativeQuery = true)
    boolean hasAppointmentWith(UUID clinicId, UUID patientId, UUID practitionerId);
}
