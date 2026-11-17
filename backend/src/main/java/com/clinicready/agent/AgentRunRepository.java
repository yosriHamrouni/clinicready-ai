package com.clinicready.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    Optional<AgentRun> findByIdAndClinicId(UUID id, UUID clinicId);

    List<AgentRun> findAllByStatusIn(List<AgentRun.Status> statuses);
}
