package com.clinicready.readiness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.clinicready.appointment.Appointment;

/**
 * Stored results, plus the appointment lookups recalculation needs. Those run for any user or a system job,
 * so they bypass per-user visibility on purpose; they still take the clinic id.
 */
interface StoredReadinessRepository extends JpaRepository<StoredReadiness, UUID> {

    Optional<StoredReadiness> findByAppointmentIdAndClinicId(UUID appointmentId, UUID clinicId);

    @Query("SELECT a FROM Appointment a WHERE a.id = :id AND a.clinicId = :clinicId")
    Optional<Appointment> findAppointment(UUID id, UUID clinicId);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.clinicId = :clinicId AND a.patientId = :patientId AND a.startsAt >= :from
              AND a.status = com.clinicready.appointment.AppointmentStatus.SCHEDULED""")
    List<Appointment> findUpcomingForPatient(UUID clinicId, UUID patientId, Instant from);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.clinicId = :clinicId AND a.appointmentTypeId = :typeId AND a.startsAt >= :from
              AND a.status = com.clinicready.appointment.AppointmentStatus.SCHEDULED""")
    List<Appointment> findUpcomingOfType(UUID clinicId, UUID typeId, Instant from);
}
