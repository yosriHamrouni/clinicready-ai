package com.clinicready.appointment.dto;

import java.util.UUID;

import com.clinicready.appointment.Practitioner;

public record PractitionerResponse(UUID id, String fullName, String specialty) {

    public static PractitionerResponse from(Practitioner p) {
        return new PractitionerResponse(p.getId(), p.getFullName(), p.getSpecialty());
    }
}
