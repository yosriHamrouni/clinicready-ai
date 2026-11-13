package com.clinicready.readiness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinicready.document.DocumentCategory;

interface RequirementRuleRepository extends JpaRepository<RequirementRule, UUID> {

    Optional<RequirementRule> findByIdAndClinicId(UUID id, UUID clinicId);

    List<RequirementRule> findAllByClinicIdAndAppointmentTypeIdOrderByDocumentCategory(UUID clinicId,
            UUID appointmentTypeId);

    boolean existsByClinicIdAndAppointmentTypeIdAndDocumentCategoryAndIdNot(UUID clinicId, UUID appointmentTypeId,
            DocumentCategory documentCategory, UUID id);
}
