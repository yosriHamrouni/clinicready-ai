package com.clinicready.patient;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every lookup takes the clinic id, so callers cannot forget the tenant filter. */
interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndClinicId(UUID id, UUID clinicId);

    Optional<Patient> findByClinicIdAndPatientRef(UUID clinicId, String patientRef);

    boolean existsByClinicIdAndPatientRef(UUID clinicId, String patientRef);

    List<Patient> findAllByClinicIdOrderByPatientRef(UUID clinicId);

    List<Patient> findAllByClinicIdAndIdIn(UUID clinicId, Collection<UUID> ids);

    /**
     * Patients whose name or reference contains the pattern, case-insensitively.
     *
     * @param pattern a lower-case LIKE pattern with {@code !} as escape character
     */
    @Query(value = """
            SELECT p.* FROM patient p
            WHERE p.clinic_id = :clinicId
              AND (lower(p.full_name) LIKE :pattern ESCAPE '!' OR lower(p.patient_ref) LIKE :pattern ESCAPE '!')
            ORDER BY p.patient_ref
            LIMIT :maxResults
            """, nativeQuery = true)
    List<Patient> search(UUID clinicId, String pattern, int maxResults);

    /** Patients with at least one appointment with the practitioner. */
    @Query(value = """
            SELECT p.* FROM patient p
            WHERE p.clinic_id = :clinicId AND EXISTS (
                SELECT 1 FROM appointment a
                WHERE a.patient_id = p.id AND a.clinic_id = :clinicId AND a.practitioner_id = :practitionerId)
            ORDER BY p.patient_ref
            """, nativeQuery = true)
    List<Patient> findAllOfPractitioner(UUID clinicId, UUID practitionerId);

    /** {@link #search} restricted to patients with at least one appointment with the practitioner. */
    @Query(value = """
            SELECT p.* FROM patient p
            WHERE p.clinic_id = :clinicId
              AND (lower(p.full_name) LIKE :pattern ESCAPE '!' OR lower(p.patient_ref) LIKE :pattern ESCAPE '!')
              AND EXISTS (
                SELECT 1 FROM appointment a
                WHERE a.patient_id = p.id AND a.clinic_id = :clinicId AND a.practitioner_id = :practitionerId)
            ORDER BY p.patient_ref
            LIMIT :maxResults
            """, nativeQuery = true)
    List<Patient> searchOfPractitioner(UUID clinicId, UUID practitionerId, String pattern, int maxResults);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM appointment a
                WHERE a.patient_id = :patientId AND a.clinic_id = :clinicId AND a.practitioner_id = :practitionerId)
            """, nativeQuery = true)
    boolean hasAppointmentWith(UUID clinicId, UUID patientId, UUID practitionerId);

    /** Highest number among the clinic's generated references (P-0001, P-0002, …); 0 if none. */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(substring(patient_ref FROM 3) AS INTEGER)), 0) FROM patient
            WHERE clinic_id = :clinicId AND patient_ref ~ '^P-[0-9]{1,9}$'
            """, nativeQuery = true)
    int maxGeneratedRefNumber(UUID clinicId);
}
