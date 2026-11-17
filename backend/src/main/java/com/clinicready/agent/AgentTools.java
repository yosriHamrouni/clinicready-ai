package com.clinicready.agent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.clinicready.appointment.AppointmentFilter;
import com.clinicready.appointment.AppointmentService;
import com.clinicready.appointment.AppointmentStatus;
import com.clinicready.appointment.AppointmentView;
import com.clinicready.common.ApiException;
import com.clinicready.common.Language;
import com.clinicready.document.AiExtractionStatus;
import com.clinicready.document.Document;
import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentExtraction;
import com.clinicready.document.DocumentMetadataField;
import com.clinicready.document.DocumentService;
import com.clinicready.document.ExtractionStatus;
import com.clinicready.followup.FollowUpService;
import com.clinicready.followup.FollowUpStatus;
import com.clinicready.readiness.AggregateStatus;
import com.clinicready.readiness.CheckStatus;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.readiness.RequirementRuleService;
import com.clinicready.readiness.dto.ReadinessCheckResponse;
import com.clinicready.readiness.dto.ReadinessResponse;

/**
 * The agent's tools (PRD "Tool contracts"). Each one calls a domain service as the run's initiator: the
 * services read the identity from the security context, so clinic scoping, role permissions and a
 * practitioner's own-appointments scope apply exactly as in the REST API. No repository is touched here.
 *
 * <p>Inputs are strings the tool parses itself, so a malformed id or date is an {@code INVALID_INPUT}
 * result rather than an exception. Outputs carry patient references, ids and categories only: no names,
 * phone numbers, file names or document text.
 */
@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    /** The agent run whose tool calls this thread is executing; set by the orchestrator, null otherwise. */
    static final ThreadLocal<UUID> CURRENT_RUN = new ThreadLocal<>();

    /** Requirement codes of the two checks every appointment has besides its type's document rules. */
    static final String EXPECTED_EXAMINATIONS = "EXPECTED_EXAMINATIONS";
    static final String ADVANCE_PAYMENT = "ADVANCE_PAYMENT";

    private final AppointmentService appointments;
    private final RequirementRuleService rules;
    private final DocumentService documents;
    private final ReadinessService readiness;
    private final FollowUpService followUps;

    AgentTools(AppointmentService appointments, RequirementRuleService rules, DocumentService documents,
            ReadinessService readiness, FollowUpService followUps) {
        this.followUps = followUps;
        this.appointments = appointments;
        this.rules = rules;
        this.documents = documents;
        this.readiness = readiness;
    }

    public record UpcomingAppointment(UUID appointmentId, UUID patientId, String patientRef, UUID appointmentTypeId,
            String appointmentType, UUID practitionerId, Instant startsAt) {
    }

    public record Requirement(String requirementCode, DocumentCategory category, boolean mandatory,
            Integer validityDays, boolean needsVerification) {
    }

    public record PatientDocument(UUID documentId, DocumentCategory category, int version, LocalDate docDate,
            ExtractionStatus extractionStatus, Double confidence, boolean needsReview) {
    }

    public record ReadinessCheckResult(String requirementCode, boolean mandatory, CheckStatus status,
            List<UUID> evidenceDocumentIds, String reason, boolean requiresHumanReview) {
    }

    public record Readiness(UUID appointmentId, AggregateStatus status, List<ReadinessCheckResult> checks) {
    }

    public record TaskStatus(UUID taskId, UUID appointmentId, String requirementCode, FollowUpStatus status,
            Instant createdAt) {
    }

    public record FollowUpTasks(List<TaskStatus> tasks) {
    }

    /** {@code outcome} is CREATED, or EXISTING when an open task for the requirement was returned instead. */
    public record DraftResult(UUID taskId, FollowUpStatus status, String outcome) {
    }

    /** One requested field: the staff correction if any (no confidence), else the AI observation. */
    public record InspectedField(String field, String value, String source, Double confidence) {
    }

    /** Where the values come from: the stored document version and the model that read it. */
    public record EvidenceRef(UUID documentId, int version, String model, Instant observedAt) {
    }

    public record Inspection(UUID documentId, DocumentCategory declaredCategory, ExtractionStatus extractionStatus,
            List<InspectedField> fields, EvidenceRef evidence) {
    }

    @Tool(name = "getUpcomingAppointments", description = """
            Scheduled appointments starting between dateFrom and dateTo (inclusive, clinic local dates, \
            at most 92 days), optionally for one practitioner. Earliest first.""")
    public ToolResult<List<UpcomingAppointment>> getUpcomingAppointments(
            @ToolParam(required = false, description = "practitioner id (UUID); omit for every practitioner")
            String practitionerId,
            @ToolParam(description = "first day, ISO date yyyy-MM-dd") String dateFrom,
            @ToolParam(description = "last day, ISO date yyyy-MM-dd") String dateTo) {
        UUID practitioner;
        LocalDate from;
        LocalDate to;
        try {
            practitioner = blank(practitionerId) ? null : UUID.fromString(practitionerId.trim());
            from = LocalDate.parse(dateFrom.trim());
            to = LocalDate.parse(dateTo.trim());
        } catch (IllegalArgumentException | DateTimeParseException | NullPointerException e) {
            return ToolResult.invalid("practitionerId must be a UUID; dateFrom and dateTo ISO dates");
        }
        if (to.isBefore(from)) {
            return ToolResult.invalid("dateTo is before dateFrom");
        }
        return call("getUpcomingAppointments", () -> {
            AppointmentFilter filter = new AppointmentFilter(
                    from.atStartOfDay(ReadinessService.CLINIC_ZONE).toInstant(),
                    to.plusDays(1).atStartOfDay(ReadinessService.CLINIC_ZONE).toInstant(),
                    practitioner, null, Set.of(AppointmentStatus.SCHEDULED));
            return appointments.views(appointments.search(filter)).stream().map(AgentTools::upcoming).toList();
        });
    }

    @Tool(name = "getAppointmentRequirements", description = """
            What an appointment type requires: one entry per document rule plus the built-in \
            EXPECTED_EXAMINATIONS and ADVANCE_PAYMENT requirements, which are always mandatory.""")
    public ToolResult<List<Requirement>> getAppointmentRequirements(
            @ToolParam(description = "appointment type id (UUID)") String appointmentTypeId) {
        UUID type = uuid(appointmentTypeId);
        if (type == null) {
            return ToolResult.invalid("appointmentTypeId must be a UUID");
        }
        return call("getAppointmentRequirements", () -> {
            List<Requirement> result = new ArrayList<>(rules.listForAppointmentType(type).stream()
                    .map(r -> new Requirement(r.getDocumentCategory().name(), r.getDocumentCategory(),
                            r.isMandatory(), r.getValidityDays(), r.isNeedsVerification()))
                    .toList());
            result.add(new Requirement(EXPECTED_EXAMINATIONS, null, true, null, false));
            result.add(new Requirement(ADVANCE_PAYMENT, null, true, null, false));
            return result;
        });
    }

    @Tool(name = "listPatientDocuments", description = """
            The patient's documents, newest version first per category, with the document date (staff \
            correction first, else the AI observation), text extraction status and AI confidence.""")
    public ToolResult<List<PatientDocument>> listPatientDocuments(
            @ToolParam(description = "patient id (UUID)") String patientId) {
        UUID patient = uuid(patientId);
        if (patient == null) {
            return ToolResult.invalid("patientId must be a UUID");
        }
        return call("listPatientDocuments", () -> documents.listForPatient(patient).stream()
                .map(this::patientDocument).toList());
    }

    @Tool(name = "checkReadiness", description = """
            Runs the deterministic readiness checks of one appointment now and returns the aggregate status \
            (INCOMPLETE, NEEDS_REVIEW, READY) and one result per requirement with its evidence document ids. \
            READY is administrative only: not a medical decision, not guaranteed coverage.""")
    public ToolResult<Readiness> checkReadiness(@ToolParam(description = "appointment id (UUID)") String appointmentId) {
        UUID appointment = uuid(appointmentId);
        if (appointment == null) {
            return ToolResult.invalid("appointmentId must be a UUID");
        }
        return call("checkReadiness", () -> readinessOf(readiness.run(appointment)));
    }

    @Tool(name = "getFollowUpStatus", description = """
            Follow-up tasks of a task id or of an appointment id, with their status, approval and delivery \
            outcome. Give exactly one of taskId or appointmentId.""")
    public ToolResult<FollowUpTasks> getFollowUpStatus(
            @ToolParam(required = false, description = "follow-up task id (UUID)") String taskId,
            @ToolParam(required = false, description = "appointment id (UUID)") String appointmentId) {
        if (blank(taskId) == blank(appointmentId)) {
            return ToolResult.invalid("give exactly one of taskId or appointmentId");
        }
        UUID id = uuid(blank(taskId) ? appointmentId : taskId);
        if (id == null) {
            return ToolResult.invalid("ids must be UUIDs");
        }
        return call("getFollowUpStatus", () -> new FollowUpTasks(
                (blank(taskId) ? followUps.forAppointment(id) : List.of(followUps.get(id))).stream()
                        .map(t -> new TaskStatus(t.getId(), t.getAppointmentId(), t.getRequirementCode(),
                                t.getStatus(), t.getCreatedAt()))
                        .toList()));
    }

    @Tool(name = "inspectDocument", description = """
            Reads selected fields of one document: category, documentDate, expiryDate, issuer. Staff corrections \
            win over the AI observation. Returns ERROR when the document's text or the AI extraction failed; \
            fields are empty while extraction is still pending.""")
    public ToolResult<Inspection> inspectDocument(
            @ToolParam(description = "document id (UUID)") String documentId,
            @ToolParam(required = false, description = "fields to read; any of category, documentDate, expiryDate, issuer")
            List<String> fields) {
        UUID id = uuid(documentId);
        if (id == null) {
            return ToolResult.invalid("documentId must be a UUID");
        }
        List<String> wanted = fields == null || fields.isEmpty() ? INSPECTABLE : fields;
        if (!INSPECTABLE.containsAll(wanted)) {
            return ToolResult.invalid("fields must be among " + INSPECTABLE);
        }
        try {
            Document d = documents.get(id);
            if (d.getExtractionStatus() == ExtractionStatus.EXTRACTION_FAILED) {
                return new ToolResult<>(ToolResult.ToolStatus.ERROR,
                        "Text could not be read from the document (" + d.getExtractionFailure() + ")", null);
            }
            DocumentExtraction ai = documents.aiExtraction(id).orElse(null);
            if (ai != null && ai.getStatus() == AiExtractionStatus.FAILED) {
                return new ToolResult<>(ToolResult.ToolStatus.ERROR,
                        "AI extraction failed (" + ai.getFailure() + ")", null);
            }
            Map<String, String> corrected = documents.metadata(id).map(m -> m.getFields()).orElse(Map.of());
            List<InspectedField> values = wanted.stream().distinct().map(f -> field(f, corrected, ai)).toList();
            return ToolResult.ok(new Inspection(d.getId(), d.getCategory(), d.getExtractionStatus(), values,
                    new EvidenceRef(d.getId(), d.getVersion(), ai == null ? null : ai.getModelName(),
                            ai == null ? null : ai.getCreatedAt())));
        } catch (ApiException e) {
            log.info("Tool inspectDocument refused: {}", e.code());
            return ToolResult.of(e);
        }
    }

    @Tool(name = "createFollowUpDraft", description = """
            Proposes a follow-up task for one requirement of an appointment that is MISSING, EXPIRED or \
            NEEDS_REVIEW in its latest readiness result. Idempotent: returns the open task if one exists \
            (outcome EXISTING) instead of creating another. The task waits for a human approval; this tool \
            sends nothing and approves nothing.""")
    public ToolResult<DraftResult> createFollowUpDraft(
            @ToolParam(description = "appointment id (UUID)") String appointmentId,
            @ToolParam(description = "requirementCode from checkReadiness") String requirementCode,
            @ToolParam(required = false, description = "FR or AR; omit for the patient's preferred language")
            String language) {
        UUID appointment = uuid(appointmentId);
        if (appointment == null || blank(requirementCode)) {
            return ToolResult.invalid("appointmentId must be a UUID and requirementCode is required");
        }
        Language lang;
        try {
            lang = blank(language) ? null : Language.valueOf(language.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ToolResult.invalid("language must be FR or AR");
        }
        return call("createFollowUpDraft", () -> {
            FollowUpService.DraftOutcome outcome = followUps.createDraft(appointment, requirementCode.trim(), lang,
                    CURRENT_RUN.get());
            return new DraftResult(outcome.task().getId(), outcome.task().getStatus(),
                    outcome.created() ? "CREATED" : "EXISTING");
        });
    }

    private static final List<String> INSPECTABLE = List.of("category", "documentDate", "expiryDate", "issuer");

    private static InspectedField field(String name, Map<String, String> corrected, DocumentExtraction ai) {
        String staff = corrected.get(name);
        if (staff != null) {
            return new InspectedField(name, staff, "STAFF_CORRECTION", null);
        }
        if (ai == null) {
            return new InspectedField(name, null, "NOT_OBSERVED", null);
        }
        Object observed = switch (name) {
            case "category" -> ai.getCategory();
            case "documentDate" -> ai.getDocumentDate();
            case "issuer" -> ai.getIssuer();
            default -> null; // the AI does not read expiry dates; only staff corrections carry one
        };
        return observed == null ? new InspectedField(name, null, "NOT_OBSERVED", ai.getConfidence())
                : new InspectedField(name, observed.toString(), "AI_OBSERVATION", ai.getConfidence());
    }

    static UpcomingAppointment upcoming(AppointmentView v) {
        return new UpcomingAppointment(v.appointment().getId(), v.patient().getId(), v.patient().getPatientRef(),
                v.type().getId(), v.type().getCode(), v.appointment().getPractitionerId(),
                v.appointment().getStartsAt());
    }

    private PatientDocument patientDocument(Document d) {
        DocumentExtraction ai = documents.aiExtraction(d.getId()).orElse(null);
        LocalDate corrected = documents.metadata(d.getId())
                .map(m -> m.getFields().get(DocumentMetadataField.DOCUMENT_DATE.key()))
                .map(LocalDate::parse).orElse(null);
        return new PatientDocument(d.getId(), d.getCategory(), d.getVersion(),
                corrected != null ? corrected : ai == null ? null : ai.getDocumentDate(),
                d.getExtractionStatus(), ai == null ? null : ai.getConfidence(), ai != null && ai.needsReview());
    }

    static Readiness readinessOf(ReadinessResponse r) {
        return new Readiness(r.appointmentId(), r.status(), r.checks().stream().map(AgentTools::check).toList());
    }

    static ReadinessCheckResult check(ReadinessCheckResponse c) {
        return new ReadinessCheckResult(c.requirementCode(), c.mandatory(), c.status(),
                c.documents().stream().map(ReadinessCheckResponse.DocumentReference::documentId).toList(),
                c.reason(), c.requiresHumanReview());
    }

    private static <T> ToolResult<T> call(String tool, Supplier<T> body) {
        try {
            return ToolResult.ok(body.get());
        } catch (ApiException e) {
            log.info("Tool {} refused: {}", tool, e.code());
            return ToolResult.of(e);
        }
    }

    private static UUID uuid(String value) {
        try {
            return blank(value) ? null : UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
