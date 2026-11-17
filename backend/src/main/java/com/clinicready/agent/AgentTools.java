package com.clinicready.agent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
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
import com.clinicready.document.Document;
import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentExtraction;
import com.clinicready.document.DocumentMetadataField;
import com.clinicready.document.DocumentService;
import com.clinicready.document.ExtractionStatus;
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

    /** Requirement codes of the two checks every appointment has besides its type's document rules. */
    static final String EXPECTED_EXAMINATIONS = "EXPECTED_EXAMINATIONS";
    static final String ADVANCE_PAYMENT = "ADVANCE_PAYMENT";

    private final AppointmentService appointments;
    private final RequirementRuleService rules;
    private final DocumentService documents;
    private final ReadinessService readiness;

    AgentTools(AppointmentService appointments, RequirementRuleService rules, DocumentService documents,
            ReadinessService readiness) {
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

    public record FollowUpStatus(List<Object> tasks) {
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
    public ToolResult<FollowUpStatus> getFollowUpStatus(
            @ToolParam(required = false, description = "follow-up task id (UUID)") String taskId,
            @ToolParam(required = false, description = "appointment id (UUID)") String appointmentId) {
        if (blank(taskId) == blank(appointmentId)) {
            return ToolResult.invalid("give exactly one of taskId or appointmentId");
        }
        UUID id = uuid(blank(taskId) ? appointmentId : taskId);
        if (id == null) {
            return ToolResult.invalid("ids must be UUIDs");
        }
        return call("getFollowUpStatus", () -> {
            if (!blank(appointmentId)) {
                appointments.get(id); // scoping: 404 for another clinic or another practitioner's appointment
            }
            // shortcut: follow-up tasks do not exist yet; YOS-32 replaces this with FollowUpService.
            return new FollowUpStatus(List.of());
        });
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
        return new ReadinessCheckResult(requirementCode(c), c.mandatory(), c.status(),
                c.documents().stream().map(ReadinessCheckResponse.DocumentReference::documentId).toList(),
                c.reason(), c.requiresHumanReview());
    }

    /** The category for a document rule (unique per appointment type), else the built-in requirement's kind. */
    static String requirementCode(ReadinessCheckResponse c) {
        return c.documentCategories().size() == 1 ? c.documentCategories().get(0).name() : c.requirement().name();
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
