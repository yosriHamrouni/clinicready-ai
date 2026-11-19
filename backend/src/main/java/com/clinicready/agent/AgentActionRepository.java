package com.clinicready.agent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {

    List<AgentAction> findAllByRunIdOrderBySeq(UUID runId);
}
