package com.clinicready.followup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.clinicready.common.Language;

interface ReminderTemplateRepository extends JpaRepository<ReminderTemplate, UUID> {

    List<ReminderTemplate> findAllByClinicIdOrderByRequirementCodeAscLanguageAsc(UUID clinicId);

    Optional<ReminderTemplate> findByClinicIdAndRequirementCodeAndLanguage(UUID clinicId, String requirementCode,
            Language language);
}
