package com.clinicready.appointment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {

    Optional<Practitioner> findByIdAndClinicId(UUID id, UUID clinicId);

    List<Practitioner> findAllByClinicIdOrderByFullName(UUID clinicId);

    List<Practitioner> findAllByClinicIdAndIdIn(UUID clinicId, Collection<UUID> ids);
}
