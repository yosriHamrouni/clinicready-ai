package com.clinicready.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every lookup takes the clinic id, so callers cannot forget the tenant filter. */
interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndClinicId(UUID id, UUID clinicId);

    List<Document> findAllByClinicIdAndPatientIdOrderByCategoryAscVersionDesc(UUID clinicId, UUID patientId);

    @Query("""
            SELECT COALESCE(MAX(d.version), 0) FROM Document d
            WHERE d.clinicId = :clinicId AND d.patientId = :patientId AND d.category = :category""")
    int maxVersion(UUID clinicId, UUID patientId, DocumentCategory category);
}
