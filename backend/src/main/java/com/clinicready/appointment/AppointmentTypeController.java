package com.clinicready.appointment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.appointment.dto.AppointmentTypeResponse;

@RestController
@RequestMapping("/api/v1/appointment-types")
class AppointmentTypeController {

    private final AppointmentService appointments;

    AppointmentTypeController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    List<AppointmentTypeResponse> list() {
        return appointments.listTypes().stream().map(AppointmentTypeResponse::from).toList();
    }
}
