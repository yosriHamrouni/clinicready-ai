package com.clinicready.followup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.appointment.Appointment;
import com.clinicready.appointment.AppointmentService;
import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.clinic.Clinic;
import com.clinicready.clinic.ClinicRepository;
import com.clinicready.common.ConflictException;
import com.clinicready.common.InvalidRequestException;
import com.clinicready.common.Language;
import com.clinicready.common.NotFoundException;
import com.clinicready.patient.Patient;
import com.clinicready.patient.PatientService;
import com.clinicready.readiness.CheckStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessResponse;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Follow-up tasks of the current user's clinic. The only class that creates tasks or changes their status.
 * A task is visible to whoever may see its appointment (a practitioner: own appointments only); anything
 * else is reported as not found.
 *
 * <p>The only path to a consequential action: a task moves past {@code AWAITING_APPROVAL} only through
 * {@link #approve} or {@link #reject}, which need {@link Permission#FOLLOWUP_APPROVE} and are reachable from
 * the approval API alone (no agent tool calls them). Delivery needs a recorded APPROVED decision.
 * Illegal transitions are {@code 409 INVALID_TRANSITION}.
 */
@Service
public class FollowUpService {

    static final String AUDIT_ENTITY = "FOLLOW_UP_TASK";
    static final int MAX_DRAFT_LENGTH = 2000;

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
    private final FollowUpApprovalRepository approvals;
    private final MessageDraftRepository drafts;
    private final ReminderTemplates templates;
    private final ClinicRepository clinics;

    FollowUpService(FollowUpTaskRepository tasks, AppointmentService appointments, PatientService patients,
            ReadinessService readiness, CurrentUserService currentUser, AuthorizationService authorization,
            AuditService audit, FollowUpApprovalRepository approvals, MessageDraftRepository drafts,
            ReminderTemplates templates, ClinicRepository clinics) {
        this.approvals = approvals;
        this.drafts = drafts;
        this.templates = templates;
        this.clinics = clinics;
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
        Patient patient = patients.get(appointment.getPatientId());
        Language lang = language != null ? language : patient.getPreferredLanguage();
        FollowUpTask task = tasks.saveAndFlush(new FollowUpTask(appointment.getClinicId(), appointment.getId(),
                appointment.getPatientId(), requirementCode, check.status(), check.reason(),
                check.remainingAction(),
                check.documents().stream().map(ReadinessCheckResponse.DocumentReference::documentId).toList(),
                lang, appointment.getStartsAt(), currentUser.userId(), runId));
        ReminderTemplates.Rendered text = templates.render(task, appointment.getStartsAt(), patient.getFullName(),
                clinicName(task.getClinicId()));
        drafts.save(new MessageDraft(task, text.body(), text.templateId()));
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

    /** A task with its decisions, oldest first. */
    public record TaskView(FollowUpTask task, List<FollowUpApproval> approvals) {
    }

    @Transactional(readOnly = true)
    public TaskView view(UUID id) {
        FollowUpTask task = get(id);
        return new TaskView(task, approvals.findAllByTaskIdOrderByDecidedAt(task.getId()));
    }

    /**
     * Open tasks of the clinic (or those with the given statuses), earliest due first, at most 200. A
     * practitioner sees only tasks of their own appointments.
     */
    @Transactional(readOnly = true)
    public List<FollowUpTask> list(Set<FollowUpStatus> statuses) {
        authorization.require(Permission.APPOINTMENT_READ);
        Set<FollowUpStatus> wanted = statuses == null || statuses.isEmpty() ? FollowUpStatus.OPEN : statuses;
        if (currentUser.role() != Role.PRACTITIONER) {
            return tasks.findAllByClinicIdAndStatusInOrderByDueAt(currentUser.clinicId(), wanted, Limit.of(200));
        }
        return currentUser.practitionerId()
                .map(own -> tasks.findForPractitioner(currentUser.clinicId(), wanted, own, Limit.of(200)))
                .orElse(List.of());
    }

    /** @throws ConflictException {@code INVALID_TRANSITION} unless the task is AWAITING_APPROVAL */
    @Transactional
    public TaskView approve(UUID id, String comment) {
        return decide(id, FollowUpApproval.Decision.APPROVED, comment);
    }

    /**
     * @throws InvalidRequestException no comment: a rejection must say why
     * @throws ConflictException       {@code INVALID_TRANSITION} unless the task is AWAITING_APPROVAL
     */
    @Transactional
    public TaskView reject(UUID id, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new InvalidRequestException("A rejection needs a comment");
        }
        return decide(id, FollowUpApproval.Decision.REJECTED, comment);
    }

    /**
     * Staff record that they delivered the approved reminder themselves (copy, call, message). Re-checks the
     * appointment's readiness.
     *
     * @throws ConflictException {@code INVALID_TRANSITION} unless the task is APPROVED
     */
    @Transactional
    public TaskView markDelivered(UUID id) {
        authorization.require(Permission.FOLLOWUP_APPROVE);
        FollowUpTask task = get(id);
        FollowUpApproval approval = task.getApprovalId() == null ? null
                : approvals.findById(task.getApprovalId()).orElse(null);
        return deliver(task, approval, null);
    }

    /**
     * Executes the approved action of a task for an agent run (the {@code submitApprovedAction} tool). Fails
     * unless {@code approvalId} is this task's recorded APPROVED decision and the task is still APPROVED: the
     * agent cannot create that decision, only a person with {@link Permission#FOLLOWUP_APPROVE} can.
     *
     * @throws ConflictException {@code APPROVAL_INVALID} for any other approval, {@code INVALID_TRANSITION}
     *                           when the task is not APPROVED (including an approval already used)
     */
    @Transactional
    public TaskView executeApproved(UUID taskId, UUID approvalId, UUID runId) {
        authorization.require(Permission.AGENT_RUN);
        FollowUpTask task = get(taskId);
        FollowUpApproval approval = approvals.findByIdAndClinicId(approvalId, task.getClinicId())
                .filter(a -> a.getTaskId().equals(task.getId()) && a.getId().equals(task.getApprovalId()))
                .filter(a -> a.getDecision() == FollowUpApproval.Decision.APPROVED)
                .orElseThrow(() -> new ConflictException("APPROVAL_INVALID",
                        "No valid approval of this task with that id"));
        return deliver(task, approval, runId);
    }

    /** Staff close a delivered task once its requirement is dealt with. */
    @Transactional
    public TaskView resolve(UUID id) {
        authorization.require(Permission.FOLLOWUP_APPROVE);
        FollowUpTask task = get(id);
        requireTransition(task, FollowUpStatus.RESOLVED);
        task.resolve();
        changed(task, AuditAction.FOLLOW_UP_TASK_RESOLVED, Map.of());
        return view(task.getId());
    }

    private TaskView decide(UUID id, FollowUpApproval.Decision decision, String comment) {
        authorization.require(Permission.FOLLOWUP_APPROVE);
        FollowUpTask task = get(id);
        requireTransition(task, decision == FollowUpApproval.Decision.APPROVED ? FollowUpStatus.APPROVED
                : FollowUpStatus.REJECTED);
        String text = comment == null || comment.isBlank() ? null : comment.strip();
        if (text != null && text.length() > 500) {
            throw new InvalidRequestException("The comment is longer than 500 characters");
        }
        FollowUpApproval approval = approvals.saveAndFlush(new FollowUpApproval(task, decision, currentUser.userId(),
                text));
        task.decide(approval);
        changed(task, decision == FollowUpApproval.Decision.APPROVED ? AuditAction.FOLLOW_UP_TASK_APPROVED
                : AuditAction.FOLLOW_UP_TASK_REJECTED, Map.of("approvalId", approval.getId().toString()));
        return view(task.getId());
    }

    private TaskView deliver(FollowUpTask task, FollowUpApproval approval, UUID runId) {
        requireTransition(task, FollowUpStatus.DELIVERED);
        if (approval == null || approval.getDecision() != FollowUpApproval.Decision.APPROVED) {
            throw new ConflictException("APPROVAL_INVALID", "The task has no recorded approval");
        }
        task.deliver(currentUser.userId(), runId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("approvalId", approval.getId().toString());
        summary.put("runId", runId == null ? null : runId.toString());
        changed(task, AuditAction.FOLLOW_UP_TASK_DELIVERED, summary);
        readiness.run(task.getAppointmentId());
        return view(task.getId());
    }

    /**
     * The task's message draft. Tasks created before drafts existed get one built now (not stored).
     *
     * @throws NotFoundException task not visible
     */
    @Transactional(readOnly = true)
    public MessageDraft draft(UUID taskId) {
        FollowUpTask task = get(taskId);
        return drafts.findByTaskId(task.getId()).orElseGet(() -> {
            Appointment appointment = appointments.get(task.getAppointmentId());
            ReminderTemplates.Rendered text = templates.render(task, appointment.getStartsAt(),
                    patients.get(task.getPatientId()).getFullName(), clinicName(task.getClinicId()));
            return new MessageDraft(task, text.body(), text.templateId());
        });
    }

    /**
     * Staff replace the draft text. Only while the task awaits a decision: an approval covers the text as it
     * was when it was approved.
     *
     * @throws InvalidRequestException empty or longer than {@value #MAX_DRAFT_LENGTH} characters
     * @throws ConflictException       {@code DRAFT_LOCKED} unless the task is AWAITING_APPROVAL
     */
    @Transactional
    public MessageDraft editDraft(UUID taskId, String body) {
        authorization.require(Permission.FOLLOWUP_APPROVE);
        FollowUpTask task = get(taskId);
        if (task.getStatus() != FollowUpStatus.AWAITING_APPROVAL) {
            throw new ConflictException("DRAFT_LOCKED", "A " + task.getStatus() + " task's draft cannot change");
        }
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_DRAFT_LENGTH) {
            throw new InvalidRequestException("A draft is 1 to " + MAX_DRAFT_LENGTH + " characters");
        }
        MessageDraft draft = draft(taskId);
        draft.edit(text, currentUser.userId());
        drafts.saveAndFlush(draft);
        audit.record(AuditAction.FOLLOW_UP_DRAFT_EDITED, AUDIT_ENTITY, task.getId(), Map.of("length", text.length()));
        return draft;
    }

    private String clinicName(UUID clinicId) {
        return clinics.findById(clinicId).map(Clinic::getName).orElse("");
    }

    private static void requireTransition(FollowUpTask task, FollowUpStatus next) {
        if (!task.getStatus().canMoveTo(next)) {
            throw new ConflictException("INVALID_TRANSITION",
                    "A " + task.getStatus() + " task cannot become " + next);
        }
    }

    private void changed(FollowUpTask task, AuditAction action, Map<String, Object> extra) {
        tasks.saveAndFlush(task);
        Map<String, Object> summary = new LinkedHashMap<>(extra);
        summary.put("status", task.getStatus().name());
        audit.record(action, AUDIT_ENTITY, task.getId(), summary);
        log.info("Follow-up task {}: {}", task.getId(), action);
    }
}
