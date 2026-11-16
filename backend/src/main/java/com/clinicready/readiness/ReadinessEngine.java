package com.clinicready.readiness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentSource;
import com.clinicready.document.DocumentStatus;
import com.clinicready.document.ExtractionStatus;
import com.clinicready.document.ReviewReason;

/**
 * Deterministic readiness core: one {@link ReadinessCheck} per requirement plus an aggregate status.
 * Plain Java, no Spring, no LLM. Callers load the facts; this class only decides.
 *
 * <p>Status decisions:
 * <ul>
 * <li>Uncertainty (extraction pending or failed, low confidence, unknown category, conflicting dates,
 * pending staff verification) is {@code NEEDS_REVIEW}, never {@code MISSING}.</li>
 * <li>Confirmed non-compliance other than expiry is a document a staff member rejected. It is reported
 * as {@code MISSING} (no acceptable document in the record) with a reason that names the rejection and
 * the rejected document IDs as evidence, so it is never hidden under {@code PRESENT}.</li>
 * <li>{@code EXPIRED} on a mandatory requirement blocks readiness (aggregate {@code INCOMPLETE}).</li>
 * <li>Optional requirements are reported with their status but never change the aggregate.</li>
 * </ul>
 * {@code READY} means administratively ready only: not medical admission, not guaranteed coverage.
 */
public final class ReadinessEngine {

    private static final String SOURCES_CHECKED = Arrays.stream(DocumentSource.values())
            .map(Enum::name).collect(Collectors.joining(", "));

    private ReadinessEngine() {
    }

    /** What a requirement is about. Document requirements match documents; the others read appointment facts. */
    public enum Kind {
        DOCUMENT,
        EXPECTED_EXAMINATIONS,
        ADVANCE_PAYMENT
    }

    /**
     * One requirement to check.
     *
     * @param acceptedCategories any one of these categories satisfies the requirement (alternatives, e.g.
     *        CNAM card or private insurance card); ignored unless {@code kind} is DOCUMENT
     * @param exemption why the requirement does not apply (configured exemption or authorized human
     *        decision); null when it applies
     */
    public record Requirement(UUID ruleId, Kind kind, Set<DocumentCategory> acceptedCategories, boolean mandatory,
            Integer validityDays, boolean needsVerification, String exemption) {

        public Requirement {
            Objects.requireNonNull(kind, "kind");
            acceptedCategories = acceptedCategories == null ? Set.of() : Set.copyOf(acceptedCategories);
            if (kind == Kind.DOCUMENT && acceptedCategories.isEmpty()) {
                throw new IllegalArgumentException("a document requirement needs at least one accepted category");
            }
        }

        public static Requirement of(RequirementRule rule) {
            return new Requirement(rule.getId(), Kind.DOCUMENT, Set.of(rule.getDocumentCategory()),
                    rule.isMandatory(), rule.getValidityDays(), rule.isNeedsVerification(), null);
        }
    }

    /**
     * What the system knows about one of the patient's documents.
     *
     * @param reviewReason why the AI extraction flagged the document; null when it did not
     * @param expiryDate expiry date read from the document; null when unknown or none
     * @param datesConflict the dates read from the document disagree with each other
     */
    public record Evidence(UUID documentId, DocumentCategory category, DocumentStatus status,
            ExtractionStatus extractionStatus, ReviewReason reviewReason, LocalDate expiryDate, Instant uploadedAt,
            boolean datesConflict) {
    }

    /**
     * Appointment facts the non-document requirements read.
     *
     * @param zone clinic time zone, to turn upload instants into days
     * @param expectedExaminations null = not entered yet; empty = none expected
     * @param advanceAmount null = not entered yet
     */
    public record AppointmentFacts(LocalDate appointmentDate, ZoneId zone, List<String> expectedExaminations,
            BigDecimal advanceAmount, boolean paymentConfirmed) {

        public AppointmentFacts {
            Objects.requireNonNull(appointmentDate, "appointmentDate");
            Objects.requireNonNull(zone, "zone");
        }
    }

    public static ReadinessResult evaluate(List<Requirement> requirements, List<Evidence> documents,
            AppointmentFacts appointment) {
        List<ReadinessCheck> checks = requirements.stream()
                .map(r -> check(r, documents, appointment))
                .toList();
        return new ReadinessResult(aggregate(checks), checks);
    }

    static AggregateStatus aggregate(List<ReadinessCheck> checks) {
        List<ReadinessCheck> mandatory = checks.stream().filter(ReadinessCheck::mandatory).toList();
        if (mandatory.stream().anyMatch(c -> c.status() == CheckStatus.MISSING || c.status() == CheckStatus.EXPIRED)) {
            return AggregateStatus.INCOMPLETE;
        }
        if (mandatory.stream().anyMatch(c -> c.status() == CheckStatus.NEEDS_REVIEW)) {
            return AggregateStatus.NEEDS_REVIEW;
        }
        return AggregateStatus.READY;
    }

    private static ReadinessCheck check(Requirement r, List<Evidence> documents, AppointmentFacts appointment) {
        if (r.exemption() != null) {
            return result(r, CheckStatus.NOT_APPLICABLE, List.of(), r.exemption());
        }
        return switch (r.kind()) {
            case DOCUMENT -> checkDocuments(r, documents, appointment);
            case EXPECTED_EXAMINATIONS -> appointment.expectedExaminations() == null
                    ? result(r, CheckStatus.NEEDS_REVIEW, List.of(), "Expected examination list not entered yet")
                    : result(r, CheckStatus.PRESENT, List.of(), appointment.expectedExaminations().isEmpty()
                            ? "No examination expected" : "Expected examination list entered");
            case ADVANCE_PAYMENT -> {
                if (appointment.advanceAmount() == null) {
                    yield result(r, CheckStatus.NEEDS_REVIEW, List.of(), "Advance amount not entered yet");
                }
                if (appointment.advanceAmount().signum() == 0) {
                    yield result(r, CheckStatus.NOT_APPLICABLE, List.of(), "No advance required (amount 0)");
                }
                yield appointment.paymentConfirmed()
                        ? result(r, CheckStatus.PRESENT, List.of(), "Advance payment confirmed by staff")
                        : result(r, CheckStatus.NEEDS_REVIEW, List.of(), "Advance payment confirmation pending");
            }
        };
    }

    private static ReadinessCheck checkDocuments(Requirement r, List<Evidence> documents, AppointmentFacts appointment) {
        List<Evidence> candidates = documents.stream()
                .filter(d -> r.acceptedCategories().contains(d.category()))
                .toList();
        String wanted = r.acceptedCategories().stream().map(Enum::name).sorted().collect(Collectors.joining(" or "));
        if (candidates.isEmpty()) {
            return result(r, CheckStatus.MISSING, List.of(),
                    "No " + wanted + " document found in the sources checked: " + SOURCES_CHECKED);
        }
        List<Evidence> usable = candidates.stream().filter(d -> d.status() != DocumentStatus.REJECTED).toList();
        if (usable.isEmpty()) {
            return result(r, CheckStatus.MISSING, ids(candidates),
                    "Every " + wanted + " document was rejected by staff (confirmed non-compliance)");
        }

        List<UUID> present = new ArrayList<>();
        List<UUID> uncertain = new ArrayList<>();
        List<String> uncertainReasons = new ArrayList<>();
        List<UUID> expired = new ArrayList<>();
        for (Evidence d : usable) {
            String doubt = doubt(d);
            if (doubt != null) {
                uncertain.add(d.documentId());
                uncertainReasons.add(doubt);
            } else if (isExpired(d, r, appointment)) {
                expired.add(d.documentId());
            } else if (r.needsVerification() && d.status() != DocumentStatus.VERIFIED) {
                uncertain.add(d.documentId());
                uncertainReasons.add("awaiting staff verification");
            } else {
                present.add(d.documentId());
            }
        }
        if (!present.isEmpty()) {
            return result(r, CheckStatus.PRESENT, present, wanted + " document found");
        }
        if (!uncertain.isEmpty()) {
            return result(r, CheckStatus.NEEDS_REVIEW, uncertain, wanted + " document found but "
                    + uncertainReasons.stream().distinct().collect(Collectors.joining("; ")));
        }
        return result(r, CheckStatus.EXPIRED, expired, wanted + " document expired before the appointment date "
                + appointment.appointmentDate());
    }

    /** Why the system cannot trust what it read from the document; null when it can. Staff verification wins. */
    private static String doubt(Evidence d) {
        if (d.status() == DocumentStatus.VERIFIED) {
            return null;
        }
        if (d.extractionStatus() == ExtractionStatus.EXTRACTION_FAILED) {
            return "text extraction failed";
        }
        if (d.extractionStatus() == ExtractionStatus.PENDING) {
            return "text extraction still pending";
        }
        if (d.reviewReason() != null) {
            return "AI extraction flagged it (" + d.reviewReason() + ")";
        }
        if (d.status() == DocumentStatus.NEEDS_REVIEW) {
            return "flagged for review by staff";
        }
        if (d.datesConflict()) {
            return "conflicting dates on the document";
        }
        return null;
    }

    private static boolean isExpired(Evidence d, Requirement r, AppointmentFacts appointment) {
        LocalDate day = appointment.appointmentDate();
        if (d.expiryDate() != null && d.expiryDate().isBefore(day)) {
            return true;
        }
        return r.validityDays() != null && d.uploadedAt() != null
                && d.uploadedAt().atZone(appointment.zone()).toLocalDate().plusDays(r.validityDays()).isBefore(day);
    }

    private static List<UUID> ids(List<Evidence> documents) {
        return documents.stream().map(Evidence::documentId).toList();
    }

    private static ReadinessCheck result(Requirement r, CheckStatus status, List<UUID> evidence, String reason) {
        return new ReadinessCheck(r.ruleId(), r.mandatory(), status, evidence, reason,
                status == CheckStatus.NEEDS_REVIEW);
    }
}
