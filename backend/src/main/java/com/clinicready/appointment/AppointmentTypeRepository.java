package com.clinicready.appointment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppointmentTypeRepository extends JpaRepository<AppointmentType, UUID> {

    Optional<AppointmentType> findByIdAndClinicId(UUID id, UUID clinicId);

    Optional<AppointmentType> findByClinicIdAndCode(UUID clinicId, String code);

    List<AppointmentType> findAllByClinicIdOrderByCode(UUID clinicId);
}
