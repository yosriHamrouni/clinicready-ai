package com.clinicready.document;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentTextRepository extends JpaRepository<DocumentText, UUID> {

    Optional<DocumentText> findByDocumentIdAndClinicId(UUID documentId, UUID clinicId);
}
