package com.clinicready.appointment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.appointment.dto.AdminDetailsRequest;
import com.clinicready.appointment.dto.NewAppointment;
import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.common.ConflictException;
import com.clinicready.common.InvalidRequestException;
import com.clinicready.common.NotFoundException;
import com.clinicready.common.PhoneNumbers;
import com.clinicready.patient.Patient;
import com.clinicready.patient.PatientService;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Appointments of the current user's clinic. A practitioner sees only their own appointments;
 * another practitioner's appointment is reported as not found.
 *
 * <p>Administrative checklist data is only ever set from a staff request: nothing here derives the
 * advance from the patient's condition, and no document upload confirms a payment. Every change is
 * audited and publishes {@link AppointmentChecklistChanged} so the checklist is recalculated.
 */
@Service
public class AppointmentService {

    static final String AUDIT_ENTITY = "APPOINTMENT";

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppointmentRepository appointments;
    private final PractitionerRepository practitioners;
    private final AppointmentTypeRepository types;
    private final PatientService patients;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    AppointmentService(AppointmentRepository appointments, PractitionerRepository practitioners,
            AppointmentTypeRepository types, PatientService patients, CurrentUserService currentUser,
            AuthorizationService authorization, AuditService audit, ApplicationEventPublisher events) {
        this.appointments = appointments;
        this.practitioners = practitioners;
        this.types = types;
        this.patients = patients;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.audit = audit;
        this.events = events;
    }

    /** Appointments starting in [from, to), earliest first. */
    @Transactional(readOnly = true)
    public List<Appointment> list(Instant from, Instant to) {
        return search(AppointmentFilter.between(from, to));
    }

    /**
     * Visible appointments matching every given criterion, earliest first. For a practitioner the
     * result is limited to their own appointments whatever {@code practitionerId} says.
     *
     * @throws InvalidRequestException if the filter is unbounded or its range is invalid
     */
    @Transactional(readOnly = true)
    public List<Appointment> search(AppointmentFilter filter) {
        authorization.require(Permission.APPOINTMENT_READ);
        filter.validate();
        UUID clinicId = currentUser.clinicId();
        Specification<Appointment> spec = (root, query, cb) -> cb.equal(root.get("clinicId"), clinicId);
        if (isPractitioner()) {
            UUID own = currentUser.practitionerId().orElse(null);
            if (own == null || (filter.practitionerId() != null && !own.equals(filter.practitionerId()))) {
                return List.of();
            }
            spec = spec.and(equal("practitionerId", own));
        } else if (filter.practitionerId() != null) {
            spec = spec.and(equal("practitionerId", filter.practitionerId()));
        }
        if (filter.patientId() != null) {
            spec = spec.and(equal("patientId", filter.patientId()));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.greaterThanOrEqualTo(root.get("startsAt"), filter.from()),
                    cb.lessThan(root.get("startsAt"), filter.to())));
        }
        if (!filter.statuses().isEmpty()) {
            Set<AppointmentStatus> statuses = filter.statuses();
            spec = spec.and((root, query, cb) -> root.get("status").in(statuses));
        }
        return appointments.findAll(spec, Sort.by("startsAt", "id"));
    }

    /** @throws NotFoundException if the appointment is in another clinic or not visible to this user */
    @Transactional(readOnly = true)
    public Appointment get(UUID id) {
        authorization.require(Permission.APPOINTMENT_READ);
        return appointments.findByIdAndClinicId(id, currentUser.clinicId())
                .filter(this::visibleToCurrentUser)
                .orElseThrow(() -> new NotFoundException("Appointment"));
    }

    /** The appointments with their patient, practitioner and type, in the same order. */
    @Transactional(readOnly = true)
    public List<AppointmentView> views(List<Appointment> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        UUID clinicId = currentUser.clinicId();
        Map<UUID, Patient> patientsById = patients.getAllById(ids(list, Appointment::getPatientId));
        Map<UUID, Practitioner> practitionersById = practitioners
                .findAllByClinicIdAndIdIn(clinicId, ids(list, Appointment::getPractitionerId)).stream()
                .collect(Collectors.toMap(Practitioner::getId, Function.identity()));
        Map<UUID, AppointmentType> typesById = types
                .findAllByClinicIdAndIdIn(clinicId, ids(list, Appointment::getAppointmentTypeId)).stream()
                .collect(Collectors.toMap(AppointmentType::getId, Function.identity()));
        // The composite foreign keys guarantee every referenced row exists in the same clinic.
        return list.stream()
                .map(a -> new AppointmentView(a, patientsById.get(a.getPatientId()),
                        practitionersById.get(a.getPractitionerId()), typesById.get(a.getAppointmentTypeId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AppointmentView view(Appointment appointment) {
        return views(List.of(appointment)).get(0);
    }

    /** @throws NotFoundException if the patient, practitioner or type is not in the user's clinic */
    @Transactional
    public Appointment create(NewAppointment request) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        UUID clinicId = currentUser.clinicId();
        patients.get(request.patientId());
        practitioners.findByIdAndClinicId(request.practitionerId(), clinicId)
                .orElseThrow(() -> new NotFoundException("Practitioner"));
        types.findByIdAndClinicId(request.appointmentTypeId(), clinicId)
                .orElseThrow(() -> new NotFoundException("Appointment type"));
        Appointment appointment = appointments.save(new Appointment(clinicId, request.patientId(),
                request.practitionerId(), request.appointmentTypeId(), request.startsAt()));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("patientId", request.patientId().toString());
        summary.put("practitionerId", request.practitionerId().toString());
        summary.put("appointmentTypeId", request.appointmentTypeId().toString());
        summary.put("startsAt", request.startsAt().toString());
        changed(appointment, AuditAction.APPOINTMENT_CREATED, summary);
        return appointment;
    }

    /**
     * Replaces the appointment's administrative data with the staff-entered values. A changed
     * trusted contact or advance drops its confirmation. A request that changes nothing writes nothing.
     *
     * @throws NotFoundException       appointment not in the clinic
     * @throws InvalidRequestException contact value does not match its method
     */
    @Transactional
    public Appointment updateAdminDetails(UUID id, AdminDetailsRequest request) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        Appointment appointment = get(id);
        AdminDetailsRequest.TrustedContact contact = normalize(request.trustedContact());
        List<String> examinations = request.expectedExaminations() == null ? null
                : request.expectedExaminations().stream().map(String::trim).toList();
        BigDecimal advance = request.advanceAmount();

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(contact == null ? null : contact.name(), appointment.getTrustedContactName())
                || (contact == null ? null : contact.method()) != appointment.getTrustedContactMethod()
                || !Objects.equals(contact == null ? null : contact.value(), appointment.getTrustedContactValue())) {
            changedFields.add("trustedContact");
        }
        if (!Objects.equals(examinations, appointment.getExpectedExaminations())) {
            changedFields.add("expectedExaminations");
        }
        BigDecimal advanceBefore = appointment.getAdvanceAmount();
        if (!Appointment.sameAmount(advance, advanceBefore)) {
            changedFields.add("advanceAmount");
        }
        if (changedFields.isEmpty()) {
            return appointment;
        }

        boolean contactWasConfirmed = appointment.getTrustedContactConfirmedAt() != null;
        boolean paymentWasConfirmed = appointment.getPaymentConfirmedAt() != null;
        appointment.setTrustedContact(contact == null ? null : contact.name(),
                contact == null ? null : contact.method(), contact == null ? null : contact.value());
        appointment.setExpectedExaminations(examinations);
        appointment.setAdvanceAmount(advance);

        // Field names and the amount only: contact details and examinations are not copied to the audit log.
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("changedFields", changedFields);
        if (changedFields.contains("advanceAmount")) {
            summary.put("advanceAmountBefore", advanceBefore == null ? null : advanceBefore.toPlainString());
            summary.put("advanceAmountAfter", advance == null ? null : advance.toPlainString());
        }
        summary.put("trustedContactConfirmationCleared",
                contactWasConfirmed && appointment.getTrustedContactConfirmedAt() == null);
        summary.put("paymentConfirmationCleared", paymentWasConfirmed && appointment.getPaymentConfirmedAt() == null);
        changed(appointment, AuditAction.APPOINTMENT_ADMIN_DETAILS_UPDATED, summary);
        return appointment;
    }

    /**
     * Records that a staff member reached the trusted contact. Already confirmed: no change.
     *
     * @throws ConflictException {@code TRUSTED_CONTACT_MISSING} if no contact is recorded
     */
    @Transactional
    public Appointment confirmTrustedContact(UUID id) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        Appointment appointment = get(id);
        if (appointment.getTrustedContactName() == null) {
            throw new ConflictException("TRUSTED_CONTACT_MISSING", "No trusted contact to confirm");
        }
        if (appointment.getTrustedContactConfirmedAt() == null) {
            appointment.confirmTrustedContact(currentUser.userId(), Instant.now());
            changed(appointment, AuditAction.TRUSTED_CONTACT_CONFIRMED, Map.of());
        }
        return appointment;
    }

    @Transactional
    public Appointment revokeTrustedContactConfirmation(UUID id) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        Appointment appointment = get(id);
        if (appointment.getTrustedContactConfirmedAt() != null) {
            appointment.confirmTrustedContact(null, null);
            changed(appointment, AuditAction.TRUSTED_CONTACT_CONFIRMATION_REVOKED, Map.of());
        }
        return appointment;
    }

    /**
     * Records that a staff member confirmed the advance was paid. This is the only way a payment
     * becomes confirmed. Already confirmed: no change.
     *
     * @throws ConflictException {@code ADVANCE_AMOUNT_MISSING} if no advance amount is recorded
     */
    @Transactional
    public Appointment confirmPayment(UUID id) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        Appointment appointment = get(id);
        if (appointment.getAdvanceAmount() == null) {
            throw new ConflictException("ADVANCE_AMOUNT_MISSING", "No advance amount to confirm a payment for");
        }
        if (appointment.getPaymentConfirmedAt() == null) {
            appointment.confirmPayment(currentUser.userId(), Instant.now());
            changed(appointment, AuditAction.PAYMENT_CONFIRMED,
                    Map.of("advanceAmount", appointment.getAdvanceAmount().toPlainString()));
        }
        return appointment;
    }

    @Transactional
    public Appointment revokePaymentConfirmation(UUID id) {
        authorization.require(Permission.APPOINTMENT_WRITE);
        Appointment appointment = get(id);
        if (appointment.getPaymentConfirmedAt() != null) {
            appointment.confirmPayment(null, null);
            changed(appointment, AuditAction.PAYMENT_CONFIRMATION_REVOKED, Map.of());
        }
        return appointment;
    }

    /** The clinic's practitioners, by name. Every role may list them (to filter the calendar). */
    @Transactional(readOnly = true)
    public List<Practitioner> listPractitioners() {
        authorization.require(Permission.APPOINTMENT_READ);
        return practitioners.findAllByClinicIdOrderByFullName(currentUser.clinicId());
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

    private void changed(Appointment appointment, AuditAction action, Map<String, Object> summary) {
        appointments.saveAndFlush(appointment);
        audit.record(action, AUDIT_ENTITY, appointment.getId(), summary);
        events.publishEvent(new AppointmentChecklistChanged(appointment.getClinicId(), appointment.getId()));
        log.info("Appointment {}: {}", appointment.getId(), action);
    }

    /** Trims the contact and checks its value against its method. */
    private static AdminDetailsRequest.TrustedContact normalize(AdminDetailsRequest.TrustedContact contact) {
        if (contact == null) {
            return null;
        }
        String value = contact.value().trim();
        boolean valid = switch (contact.method()) {
            case PHONE -> PhoneNumbers.isWellFormed(value);
            case EMAIL -> EMAIL.matcher(value).matches();
        };
        if (!valid) {
            throw new InvalidRequestException("Trusted contact value is not a valid " + contact.method());
        }
        return new AdminDetailsRequest.TrustedContact(contact.name().trim(), contact.method(), value);
    }

    private static Specification<Appointment> equal(String attribute, Object value) {
        return (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static Set<UUID> ids(List<Appointment> list, Function<Appointment, UUID> id) {
        return list.stream().map(id).collect(Collectors.toSet());
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
