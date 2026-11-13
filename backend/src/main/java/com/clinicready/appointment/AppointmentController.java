package com.clinicready.appointment;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.clinicready.appointment.dto.AdminDetailsRequest;
import com.clinicready.appointment.dto.AppointmentResponse;
import com.clinicready.appointment.dto.NewAppointment;
import com.clinicready.appointment.dto.PractitionerResponse;

/** Appointments and their checklist data. Permissions and scoping are applied in {@link AppointmentService}. */
@RestController
@RequestMapping("/api/v1")
class AppointmentController {

    private final AppointmentService appointments;

    AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping("/appointments")
    List<AppointmentResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Set<AppointmentStatus> status) {
        AppointmentFilter filter = new AppointmentFilter(from, to, practitionerId, patientId, status);
        return appointments.views(appointments.search(filter)).stream().map(AppointmentResponse::from).toList();
    }

    @GetMapping("/appointments/{id}")
    AppointmentResponse get(@PathVariable UUID id) {
        return respond(appointments.get(id));
    }

    @PostMapping("/appointments")
    ResponseEntity<AppointmentResponse> create(@Valid @RequestBody NewAppointment request) {
        Appointment appointment = appointments.create(request);
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + appointment.getId()))
                .body(respond(appointment));
    }

    @PutMapping("/appointments/{id}/admin-details")
    AppointmentResponse updateAdminDetails(@PathVariable UUID id, @Valid @RequestBody AdminDetailsRequest request) {
        return respond(appointments.updateAdminDetails(id, request));
    }

    @PostMapping("/appointments/{id}/trusted-contact-confirmation")
    AppointmentResponse confirmTrustedContact(@PathVariable UUID id) {
        return respond(appointments.confirmTrustedContact(id));
    }

    @DeleteMapping("/appointments/{id}/trusted-contact-confirmation")
    AppointmentResponse revokeTrustedContactConfirmation(@PathVariable UUID id) {
        return respond(appointments.revokeTrustedContactConfirmation(id));
    }

    @PostMapping("/appointments/{id}/payment-confirmation")
    AppointmentResponse confirmPayment(@PathVariable UUID id) {
        return respond(appointments.confirmPayment(id));
    }

    @DeleteMapping("/appointments/{id}/payment-confirmation")
    AppointmentResponse revokePaymentConfirmation(@PathVariable UUID id) {
        return respond(appointments.revokePaymentConfirmation(id));
    }

    @GetMapping("/practitioners")
    List<PractitionerResponse> practitioners() {
        return appointments.listPractitioners().stream().map(PractitionerResponse::from).toList();
    }

    private AppointmentResponse respond(Appointment appointment) {
        return AppointmentResponse.from(appointments.view(appointment));
    }
}
