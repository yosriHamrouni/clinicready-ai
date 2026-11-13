package com.clinicready.appointment.dto;

import java.util.UUID;

import com.clinicready.appointment.AppointmentType;

public record AppointmentTypeResponse(UUID id, String code, String label) {

    public static AppointmentTypeResponse from(AppointmentType t) {
        return new AppointmentTypeResponse(t.getId(), t.getCode(), t.getLabel());
    }
}
