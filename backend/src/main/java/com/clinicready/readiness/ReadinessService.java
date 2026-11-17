package com.clinicready.readiness;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.appointment.Appointment;
import com.clinicready.appointment.AppointmentChecklistChanged;
import com.clinicready.appointment.AppointmentService;
import com.clinicready.common.NotFoundException;
import com.clinicready.document.Document;
import com.clinicready.document.DocumentExtraction;
import com.clinicready.document.DocumentMetadataField;
import com.clinicready.document.DocumentService;
import com.clinicready.document.DocumentService.DocumentFacts;
import com.clinicready.document.PatientDocumentsChanged;
import com.clinicready.readiness.ReadinessEngine.AppointmentFacts;
import com.clinicready.readiness.ReadinessEngine.Evidence;
import com.clinicready.readiness.ReadinessEngine.Kind;
import com.clinicready.readiness.ReadinessEngine.Requirement;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessCheckResponse.DocumentReference;
import com.clinicready.readiness.dto.ReadinessCheckResponse.HumanConfirmation;
import com.clinicready.readiness.dto.ReadinessResponse;
import com.clinicready.security.UserDirectory;

/**
 * Runs the {@link ReadinessEngine} for appointments and keeps each appointment's latest result.
 *
 * <p>Reading or running a check needs the same access as reading the appointment (a practitioner: own
 * appointments only), so evidence references reach only users of the clinic who may see the patient.
 * Recalculation also runs, in the same transaction, whenever an input changes: administrative data or
 * payment confirmation ({@link AppointmentChecklistChanged}), the patient's documents
 * ({@link PatientDocumentsChanged}) and the appointment type's requirement rules.
 *
 * <p>Every appointment is checked against its type's document rules plus two built-in mandatory
 * requirements: the expected examination list and the advance payment.
 */
@Service
public class ReadinessService {

    // shortcut: single-time-zone MVP; move to a per-clinic setting when clinics span zones.
    static final ZoneId CLINIC_ZONE = ZoneId.of("Africa/Tunis");
    /** Recalculate appointments from yesterday on: older ones no longer need a checklist. */
    private static final Duration RECALCULATION_WINDOW = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(ReadinessService.class);

    private final StoredReadinessRepository results;
    private final RequirementRuleRepository rules;
    private final AppointmentService appointments;
    private final DocumentService documents;
    private final UserDirectory users;

    ReadinessService(StoredReadinessRepository results, RequirementRuleRepository rules,
            AppointmentService appointments, DocumentService documents, UserDirectory users) {
        this.results = results;
        this.rules = rules;
        this.appointments = appointments;
        this.documents = documents;
        this.users = users;
    }

    /**
     * Runs the checks now and stores the result.
     *
     * @throws NotFoundException appointment not in the clinic or not visible to this user
     */
    @Transactional
    public ReadinessResponse run(UUID appointmentId) {
        return response(recalculate(appointments.get(appointmentId)));
    }

    /**
     * The latest stored result.
     *
     * @throws NotFoundException appointment not visible, or never checked
     */
    @Transactional(readOnly = true)
    public ReadinessResponse latest(UUID appointmentId) {
        Appointment appointment = appointments.get(appointmentId);
        return results.findByAppointmentIdAndClinicId(appointment.getId(), appointment.getClinicId())
                .map(ReadinessService::response)
                .orElseThrow(() -> new NotFoundException("Readiness result"));
    }

    @EventListener
    @Transactional
    void onChecklistChanged(AppointmentChecklistChanged event) {
        results.findAppointment(event.appointmentId(), event.clinicId()).ifPresent(this::recalculate);
    }

    @EventListener
    @Transactional
    void onDocumentsChanged(PatientDocumentsChanged event) {
        results.findUpcomingForPatient(event.clinicId(), event.patientId(), windowStart())
                .forEach(this::recalculate);
    }

    /** Called by {@link RequirementRuleService} after a rule of the type changes. */
    void onRulesChanged(UUID clinicId, UUID appointmentTypeId) {
        results.findUpcomingOfType(clinicId, appointmentTypeId, windowStart()).forEach(this::recalculate);
    }

    private static Instant windowStart() {
        return Instant.now().minus(RECALCULATION_WINDOW);
    }

    private StoredReadiness recalculate(Appointment appointment) {
        List<Requirement> requirements = new ArrayList<>();
        rules.findAllByClinicIdAndAppointmentTypeIdOrderByDocumentCategory(appointment.getClinicId(),
                appointment.getAppointmentTypeId()).forEach(rule -> requirements.add(Requirement.of(rule)));
        requirements.add(new Requirement(null, Kind.EXPECTED_EXAMINATIONS, null, true, null, false, null));
        requirements.add(new Requirement(null, Kind.ADVANCE_PAYMENT, null, true, null, false, null));

        List<DocumentFacts> facts = documents.currentDocuments(appointment.getClinicId(), appointment.getPatientId());
        Map<UUID, Document> byId = facts.stream().map(DocumentFacts::document)
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        ReadinessResult result = ReadinessEngine.evaluate(requirements,
                facts.stream().map(ReadinessService::evidence).toList(),
                new AppointmentFacts(appointment.getStartsAt().atZone(CLINIC_ZONE).toLocalDate(), CLINIC_ZONE,
                        appointment.getExpectedExaminations(), appointment.getAdvanceAmount(),
                        appointment.getPaymentConfirmedAt() != null));

        List<ReadinessCheckResponse> checks = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            checks.add(describe(requirements.get(i), result.checks().get(i), byId, appointment));
        }
        StoredReadiness stored = results.findById(appointment.getId())
                .orElseGet(() -> new StoredReadiness(appointment.getId(), appointment.getClinicId()));
        stored.replace(result.status(), checks, Instant.now());
        stored = results.save(stored);
        log.info("Readiness of appointment {} recalculated: {}", appointment.getId(), result.status());
        return stored;
    }

    /** Staff corrections win over the AI observation; the AI's review flag stays until staff verify. */
    static Evidence evidence(DocumentFacts facts) {
        Document d = facts.document();
        DocumentExtraction ai = facts.extraction();
        Map<String, String> corrected = facts.metadata() == null ? Map.of() : facts.metadata().getFields();
        String correctedDate = corrected.get(DocumentMetadataField.DOCUMENT_DATE.key());
        String correctedExpiry = corrected.get(DocumentMetadataField.EXPIRY_DATE.key());
        LocalDate documentDate = correctedDate != null ? LocalDate.parse(correctedDate)
                : ai == null ? null : ai.getDocumentDate();
        LocalDate expiry = correctedExpiry == null ? null : LocalDate.parse(correctedExpiry);
        return new Evidence(d.getId(), d.getCategory(), d.getStatus(), d.getExtractionStatus(),
                ai == null ? null : ai.getReviewReason(), expiry, d.getUploadedAt(),
                documentDate != null && expiry != null && documentDate.isAfter(expiry));
    }

    private ReadinessCheckResponse describe(Requirement r, ReadinessCheck c, Map<UUID, Document> byId,
            Appointment appointment) {
        List<DocumentReference> refs = c.evidenceDocumentIds().stream().map(byId::get)
                .map(d -> new DocumentReference(d.getId(), d.getCategory(), d.getVersion(), d.getStatus(),
                        d.getUploadedAt()))
                .toList();
        List<HumanConfirmation> confirmations = r.kind() == Kind.ADVANCE_PAYMENT
                && appointment.getPaymentConfirmedAt() != null
                        ? List.of(new HumanConfirmation("PAYMENT_CONFIRMED", appointment.getPaymentConfirmedBy(),
                                users.fullName(appointment.getPaymentConfirmedBy(), appointment.getClinicId())
                                        .orElse(null),
                                appointment.getPaymentConfirmedAt()))
                        : List.of();
        return new ReadinessCheckResponse(r.ruleId(), r.kind(), r.acceptedCategories().stream().sorted().toList(),
                c.mandatory(), c.status() != CheckStatus.NOT_APPLICABLE, c.status(), c.reason(),
                remainingAction(r, c, appointment), c.requiresHumanReview(), refs, confirmations);
    }

    static String remainingAction(Requirement r, ReadinessCheck c, Appointment appointment) {
        String wanted = r.acceptedCategories().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(" or "));
        return switch (c.status()) {
            case PRESENT, NOT_APPLICABLE -> null;
            case MISSING -> "Obtain and upload a " + wanted + " document";
            case EXPIRED -> "Obtain a valid " + wanted + " document";
            case NEEDS_REVIEW -> switch (r.kind()) {
                case DOCUMENT -> "Review the " + wanted + " document, then verify or correct it";
                case EXPECTED_EXAMINATIONS -> "Enter the expected examination list";
                case ADVANCE_PAYMENT -> appointment.getAdvanceAmount() == null ? "Enter the advance amount"
                        : "Confirm the advance payment once received";
            };
        };
    }

    private static ReadinessResponse response(StoredReadiness stored) {
        return ReadinessResponse.of(stored.getAppointmentId(), stored.getStatus(), stored.getEvaluatedAt(),
                stored.getChecks());
    }
}
