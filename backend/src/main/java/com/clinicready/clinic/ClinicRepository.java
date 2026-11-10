package com.clinicready.clinic;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ClinicRepository extends JpaRepository<Clinic, UUID> {
}
