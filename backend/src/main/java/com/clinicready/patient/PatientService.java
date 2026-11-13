package com.clinicready.patient;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.common.ConflictException;
import com.clinicready.common.Language;
import com.clinicready.common.NotFoundException;
import com.clinicready.patient.dto.NewPatient;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Patients of the current user's clinic; a practitioner sees only patients they have an appointment
 * with. Other features use this service, never the repository.
 *
 * <p>Never logs names or phone numbers, only ids and references.
 */
@Service
public class PatientService {

    /** Most rows a search returns; staff narrow the query rather than page. */
    public static final int SEARCH_LIMIT = 200;

    static final String AUDIT_ENTITY = "PATIENT";

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patients;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;
    private final AuditService audit;

    PatientService(PatientRepository patients, CurrentUserService currentUser, AuthorizationService authorization,
            AuditService audit) {
        this.patients = patients;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Patient> list() {
        authorization.require(Permission.PATIENT_READ);
        UUID clinicId = currentUser.clinicId();
        if (!isPractitioner()) {
            return patients.findAllByClinicIdOrderByPatientRef(clinicId);
        }
        return currentUser.practitionerId()
                .map(own -> patients.findAllOfPractitioner(clinicId, own))
                .orElse(List.of());
    }

    /**
     * Visible patients whose name or reference contains {@code query} (case-insensitive), by
     * reference, at most {@link #SEARCH_LIMIT}. A blank query matches everyone.
     */
    @Transactional(readOnly = true)
    public List<Patient> search(String query) {
        authorization.require(Permission.PATIENT_READ);
        UUID clinicId = currentUser.clinicId();
        String pattern = likePattern(query);
        if (!isPractitioner()) {
            return patients.search(clinicId, pattern, SEARCH_LIMIT);
        }
        return currentUser.practitionerId()
                .map(own -> patients.searchOfPractitioner(clinicId, own, pattern, SEARCH_LIMIT))
                .orElse(List.of());
    }

    /** @throws NotFoundException if the patient is in another clinic or not visible to this user */
    @Transactional(readOnly = true)
    public Patient get(UUID id) {
        authorization.require(Permission.PATIENT_READ);
        UUID clinicId = currentUser.clinicId();
        return patients.findByIdAndClinicId(id, clinicId)
                .filter(patient -> visibleToCurrentUser(clinicId, patient))
                .orElseThrow(() -> new NotFoundException("Patient"));
    }

    /**
     * Patients of the user's clinic by id, for display next to records the caller has already
     * authorized (such as appointments the user may see). Ids of other clinics are left out.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Patient> getAllById(Collection<UUID> ids) {
        authorization.require(Permission.PATIENT_READ);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patients.findAllByClinicIdAndIdIn(currentUser.clinicId(), ids).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity()));
    }

    /**
     * @throws ConflictException {@code PATIENT_REF_CONFLICT} if the reference is already used in the clinic
     */
    @Transactional
    public Patient create(NewPatient request) {
        authorization.require(Permission.PATIENT_WRITE);
        UUID clinicId = currentUser.clinicId();
        String ref = request.patientRef() == null || request.patientRef().isBlank()
                ? nextRef(clinicId)
                : request.patientRef().trim();
        if (patients.existsByClinicIdAndPatientRef(clinicId, ref)) {
            throw refConflict();
        }
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        Language language = request.preferredLanguage() != null ? request.preferredLanguage() : Language.FR;
        Patient patient = new Patient(clinicId, ref, request.fullName().trim(), phone, language);
        try {
            patients.saveAndFlush(patient);
        } catch (DataIntegrityViolationException e) {
            throw refConflict(); // two concurrent creates with the same reference
        }
        audit.record(AuditAction.PATIENT_CREATED, AUDIT_ENTITY, patient.getId(), Map.of("patientRef", ref));
        log.info("Patient {} created with ref {}", patient.getId(), ref);
        return patient;
    }

    private String nextRef(UUID clinicId) {
        return String.format("P-%04d", patients.maxGeneratedRefNumber(clinicId) + 1);
    }

    private static ConflictException refConflict() {
        return new ConflictException("PATIENT_REF_CONFLICT", "Patient reference already used in this clinic");
    }

    /** Lower-case LIKE pattern matching {@code query} anywhere, with LIKE wildcards escaped. */
    static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private boolean visibleToCurrentUser(UUID clinicId, Patient patient) {
        if (!isPractitioner()) {
            return true;
        }
        return currentUser.practitionerId()
                .filter(own -> patients.hasAppointmentWith(clinicId, patient.getId(), own))
                .isPresent();
    }

    private boolean isPractitioner() {
        return currentUser.role() == Role.PRACTITIONER;
    }
}
