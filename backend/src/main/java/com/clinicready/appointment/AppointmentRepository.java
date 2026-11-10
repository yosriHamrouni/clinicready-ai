package com.clinicready.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findByIdAndClinicId(UUID id, UUID clinicId);

    /** Appointments starting in [from, to), earliest first. */
    @Query("""
            select a from Appointment a
            where a.clinicId = :clinicId and a.startsAt >= :from and a.startsAt < :to
            order by a.startsAt
            """)
    List<Appointment> findStartingBetween(UUID clinicId, Instant from, Instant to);

    /** Same as {@link #findStartingBetween}, for one practitioner. */
    @Query("""
            select a from Appointment a
            where a.clinicId = :clinicId and a.practitionerId = :practitionerId
              and a.startsAt >= :from and a.startsAt < :to
            order by a.startsAt
            """)
    List<Appointment> findStartingBetweenForPractitioner(UUID clinicId, UUID practitionerId, Instant from,
            Instant to);
}
