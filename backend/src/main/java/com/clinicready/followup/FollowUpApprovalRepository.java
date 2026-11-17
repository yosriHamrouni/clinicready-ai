package com.clinicready.followup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FollowUpApprovalRepository extends JpaRepository<FollowUpApproval, UUID> {

    Optional<FollowUpApproval> findByIdAndClinicId(UUID id, UUID clinicId);

    List<FollowUpApproval> findAllByTaskIdOrderByDecidedAt(UUID taskId);
}
