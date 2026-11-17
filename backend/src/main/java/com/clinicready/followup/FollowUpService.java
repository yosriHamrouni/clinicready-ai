package com.clinicready.followup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.appointment.Appointment;
import com.clinicready.appointment.AppointmentService;
import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.common.InvalidRequestException;
import com.clinicready.common.Language;
import com.clinicready.common.NotFoundException;
import com.clinicready.patient.PatientService;
import com.clinicready.readiness.CheckStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessResponse;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;

/**
 * Follow-up tasks of the current user's clinic. The only class that creates tasks or changes their status.
 * A task is visible to whoever may see its appointment (a practitioner: own appointments only); anything
 * else is reported as not found.
 */
@Service
public class FollowUpService {

    static final String AUDIT_ENTITY = "FOLLOW_UP_TASK";

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);
    private static final Set<CheckStatus> ACTIONABLE = Set.of(CheckStatus.MISSING, CheckStatus.NEEDS_REVIEW,
            CheckStatus.EXPIRED);

    /** The task, and whether this call created it ({@code false}: an open task already existed). */
    public record DraftOutcome(FollowUpTask task, boolean created) {
    }

    private final FollowUpTaskRepository tasks;
    private final AppointmentService appointments;
    private final PatientService patients;
    private final ReadinessService readiness;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;
    private final AuditService audit;

    FollowUpService(FollowUpTaskRepository tasks, AppointmentService appointments, PatientService patients,
            ReadinessService readiness, CurrentUserService currentUser, AuthorizationService authorization,
            AuditService audit) {
        this.tasks = tasks;
        this.appointments = appointments;
        this.patients = patients;
        this.readiness = readiness;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.audit = audit;
    }

    /**
     * Proposes a follow-up for one requirement of an appointment, based on its latest readiness result, or
     * returns the open task that already exists for it. Idempotent: calling it twice creates one task.
     * Creates a task {@code AWAITING_APPROVAL} only; never changes readiness, approvals or delivery.
     *
     * @param language null: the patient's preferred language
     * @param runId    the agent run that asked for it; null when staff did
     * @throws NotFoundException       appointment not visible
     * @throws InvalidRequestException never checked, unknown requirement code, or nothing left to do for it
     */
    @Transactional
    public DraftOutcome createDraft(UUID appointmentId, String requirementCode, Language language, UUID runId) {
        authorization.require(Permission.AGENT_RUN);
        Appointment appointment = appointments.get(appointmentId);
        ReadinessResponse latest;
        try {
            latest = readiness.latest(appointment.getId());
        } catch (NotFoundException e) {
            throw new InvalidRequestException("The appointment has no readiness result yet; run the checks first");
        }
        ReadinessCheckResponse check = latest.checks().stream()
                .filter(c -> c.requirementCode().equals(requirementCode))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("Unknown requirement code for this appointment"));
        if (!ACTIONABLE.contains(check.status())) {
            throw new InvalidRequestException("Requirement " + requirementCode + " is " + check.status()
                    + ": no follow-up needed");
        }

        Optional<FollowUpTask> open = tasks.findFirstByClinicIdAndAppointmentIdAndRequirementCodeAndStatusIn(
                appointment.getClinicId(), appointment.getId(), requirementCode, FollowUpStatus.OPEN);
        if (open.isPresent()) {
            return new DraftOutcome(open.get(), false);
        }
        Language lang = language != null ? language
                : patients.get(appointment.getPatientId()).getPreferredLanguage();
        FollowUpTask task = tasks.saveAndFlush(new FollowUpTask(appointment.getClinicId(), appointment.getId(),
                appointment.getPatientId(), requirementCode, check.status(), check.reason(),
                check.remainingAction(),
                check.documents().stream().map(ReadinessCheckResponse.DocumentReference::documentId).toList(),
                lang, appointment.getStartsAt(), currentUser.userId(), runId));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("appointmentId", appointment.getId().toString());
        summary.put("requirementCode", requirementCode);
        summary.put("status", task.getStatus().name());
        summary.put("runId", runId == null ? null : runId.toString());
        audit.record(AuditAction.FOLLOW_UP_TASK_CREATED, AUDIT_ENTITY, task.getId(), summary);
        log.info("Follow-up task {} created: appointment={} requirement={}", task.getId(), appointment.getId(),
                requirementCode);
        return new DraftOutcome(task, true);
    }

    /** @throws NotFoundException task not in the clinic, or its appointment not visible to this user */
    @Transactional(readOnly = true)
    public FollowUpTask get(UUID id) {
        authorization.require(Permission.APPOINTMENT_READ);
        FollowUpTask task = tasks.findByIdAndClinicId(id, currentUser.clinicId())
                .orElseThrow(() -> new NotFoundException("Follow-up task"));
        try {
            appointments.get(task.getAppointmentId());
        } catch (NotFoundException e) {
            throw new NotFoundException("Follow-up task");
        }
        return task;
    }

    /** Every task of the appointment, oldest first. @throws NotFoundException appointment not visible */
    @Transactional(readOnly = true)
    public List<FollowUpTask> forAppointment(UUID appointmentId) {
        Appointment appointment = appointments.get(appointmentId);
        return tasks.findAllByClinicIdAndAppointmentIdOrderByCreatedAt(appointment.getClinicId(),
                appointment.getId());
    }
}
