package com.clinicready.followup;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MessageDraftRepository extends JpaRepository<MessageDraft, UUID> {

    Optional<MessageDraft> findByTaskId(UUID taskId);
}
