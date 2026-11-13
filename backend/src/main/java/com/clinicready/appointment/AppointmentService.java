package com.clinicready.appointment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.appointment.dto.NewAppointment;
import com.clinicready.common.NotFoundException;
import com.clinicready.patient.PatientService;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Appointments of the current user's clinic. A practitioner sees only their own appointments;
 * another practitioner's appointment is reported as not found.
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final PractitionerRepository practitioners;
    private final AppointmentTypeRepository types;
    private final PatientService patients;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;

    AppointmentService(AppointmentRepository appointments, PractitionerRepository practitioners,
            AppointmentTypeRepository types, PatientService patients, CurrentUserService currentUser,
            AuthorizationService authorization) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.types = types;
        this.patients = patients;
        this.currentUser = currentUser;
        this.authorization = authorization;
    }

    /** Appointments starting in [from, to), earliest first. */
    @Transactional(readOnly = true)
    public List<Appointment> list(Instant from, Instant to) {
        authorization.require(Permission.APPOINTMENT_READ);
        UUID clinicId = currentUser.clinicId();
        if (!isPractitioner()) {
            return appointments.findStartingBetween(clinicId, from, to);
        }
        return currentUser.practitionerId()
                .map(own -> appointments.findStartingBetweenForPractitioner(clinicId, own, from, to))
                .orElse(List.of());
    }

    /** @throws NotFoundException if the appointment is in another clinic or not visible to this user */
    @Transactional(readOnly = true)
    public Appointment get(UUID id) {
        authorization.require(Permission.APPOINTMENT_READ);
        return appointments.findByIdAndClinicId(id, currentUser.clinicId())
                .filter(this::visibleToCurrentUser)
                .orElseThrow(() -> new NotFoundException("Appointment"));
    }

    /** @throws NotFoundException if the patient, practitioner or type is not in the user's clinic */
    @Transactional
    public Appointment create(NewAppointment appointment) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        UUID clinicId = currentUser.clinicId();
        patients.get(appointment.patientId());
        practitioners.findByIdAndClinicId(appointment.practitionerId(), clinicId)
                .orElseThrow(() -> new NotFoundException("Practitioner"));
        types.findByIdAndClinicId(appointment.appointmentTypeId(), clinicId)
                .orElseThrow(() -> new NotFoundException("Appointment type"));
        return appointments.save(new Appointment(clinicId, appointment.patientId(), appointment.practitionerId(),
                appointment.appointmentTypeId(), appointment.startsAt()));
    }

    /** The clinic's appointment types, by code. */
    @Transactional(readOnly = true)
    public List<AppointmentType> listTypes() {
        authorization.require(Permission.APPOINTMENT_READ);
        return types.findAllByClinicIdOrderByCode(currentUser.clinicId());
    }

    /** @throws NotFoundException if the type is in another clinic */
    @Transactional(readOnly = true)
    public AppointmentType getType(UUID id) {
        authorization.require(Permission.APPOINTMENT_READ);
        return types.findByIdAndClinicId(id, currentUser.clinicId())
                .orElseThrow(() -> new NotFoundException("Appointment type"));
    }

    private boolean visibleToCurrentUser(Appointment appointment) {
        if (!isPractitioner()) {
            return true;
        }
        return currentUser.practitionerId().filter(appointment.getPractitionerId()::equals).isPresent();
    }

    private boolean isPractitioner() {
        return currentUser.role() == Role.PRACTITIONER;
    }
}
