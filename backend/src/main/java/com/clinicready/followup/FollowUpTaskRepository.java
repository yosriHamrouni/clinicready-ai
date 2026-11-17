package com.clinicready.followup;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, UUID> {

    Optional<FollowUpTask> findByIdAndClinicId(UUID id, UUID clinicId);

    List<FollowUpTask> findAllByClinicIdAndAppointmentIdOrderByCreatedAt(UUID clinicId, UUID appointmentId);

    Optional<FollowUpTask> findFirstByClinicIdAndAppointmentIdAndRequirementCodeAndStatusIn(UUID clinicId,
            UUID appointmentId, String requirementCode, Collection<FollowUpStatus> statuses);
}
