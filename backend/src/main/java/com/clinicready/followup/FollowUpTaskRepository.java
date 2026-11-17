package com.clinicready.followup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, UUID> {

    Optional<FollowUpTask> findByIdAndClinicId(UUID id, UUID clinicId);

    List<FollowUpTask> findAllByClinicIdAndAppointmentIdOrderByCreatedAt(UUID clinicId, UUID appointmentId);

    List<FollowUpTask> findAllByClinicIdAndStatusInOrderByDueAt(UUID clinicId, Collection<FollowUpStatus> statuses,
            Limit limit);

    @Query("""
            SELECT t FROM FollowUpTask t, Appointment a
            WHERE a.id = t.appointmentId AND a.clinicId = t.clinicId AND t.clinicId = :clinicId
              AND t.status IN :statuses AND a.practitionerId = :practitionerId
            ORDER BY t.dueAt""")
    List<FollowUpTask> findForPractitioner(UUID clinicId, Collection<FollowUpStatus> statuses, UUID practitionerId,
            Limit limit);

    Optional<FollowUpTask> findFirstByClinicIdAndAppointmentIdAndRequirementCodeAndStatusIn(UUID clinicId,
            UUID appointmentId, String requirementCode, Collection<FollowUpStatus> statuses);
}
