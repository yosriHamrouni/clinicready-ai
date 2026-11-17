package com.clinicready.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.clinicready.document.DocumentCategory;
import com.clinicready.document.DocumentStatus;
import com.clinicready.document.ExtractionStatus;
import com.clinicready.document.ReviewReason;
import com.clinicready.readiness.ReadinessEngine.AppointmentFacts;
import com.clinicready.readiness.ReadinessEngine.Evidence;
import com.clinicready.readiness.ReadinessEngine.Kind;
import com.clinicready.readiness.ReadinessEngine.Requirement;

class ReadinessEngineTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 17);
    private static final AppointmentFacts APPOINTMENT = new AppointmentFacts(DAY, TUNIS, List.of(), BigDecimal.ZERO, false);
    private static final Instant RECENT = Instant.parse("2026-11-10T09:00:00Z");

    private static Requirement mandatory(DocumentCategory... categories) {
        return new Requirement(UUID.randomUUID(), Kind.DOCUMENT, Set.of(categories), true, null, false, null);
    }

    private static Requirement optional(DocumentCategory category) {
        return new Requirement(UUID.randomUUID(), Kind.DOCUMENT, Set.of(category), false, null, false, null);
    }

    private static Evidence readable(DocumentCategory category) {
        return new Evidence(UUID.randomUUID(), category, DocumentStatus.UPLOADED, ExtractionStatus.EXTRACTED, null,
                null, RECENT, false);
    }

    private static ReadinessResult evaluate(List<Requirement> rules, List<Evidence> docs) {
        return ReadinessEngine.evaluate(rules, docs, APPOINTMENT);
    }

    // PRD evaluation cases 1-8 (docs: eval/README.md).

    @Test
    void case1AllDocumentsPresentIsReady() {
        Evidence id = readable(DocumentCategory.ID_DOCUMENT);
        Evidence referral = readable(DocumentCategory.REFERRAL_LETTER);

        var result = evaluate(List.of(mandatory(DocumentCategory.ID_DOCUMENT), mandatory(DocumentCategory.REFERRAL_LETTER)),
                List.of(id, referral));

        assertThat(result.status()).isEqualTo(AggregateStatus.READY);
        assertThat(result.checks()).extracting(ReadinessCheck::status).containsOnly(CheckStatus.PRESENT);
        assertThat(result.checks().get(0).evidenceDocumentIds()).containsExactly(id.documentId());
        assertThat(result.checks()).noneMatch(ReadinessCheck::requiresHumanReview);
    }

    @Test
    void case2MissingMandatoryDocumentIsIncompleteAndNamesSourcesChecked() {
        var result = evaluate(List.of(mandatory(DocumentCategory.REFERRAL_LETTER)), List.of(readable(DocumentCategory.ID_DOCUMENT)));

        assertThat(result.status()).isEqualTo(AggregateStatus.INCOMPLETE);
        ReadinessCheck check = result.checks().get(0);
        assertThat(check.status()).isEqualTo(CheckStatus.MISSING);
        assertThat(check.reason()).contains("REFERRAL_LETTER", "STAFF_UPLOAD", "EMAIL", "SCAN", "PATIENT_SUBMISSION");
        assertThat(check.evidenceDocumentIds()).isEmpty();
    }

    @Test
    void case3MissingOptionalDocumentStaysVisibleButIsReady() {
        var result = evaluate(List.of(mandatory(DocumentCategory.ID_DOCUMENT), optional(DocumentCategory.CONSENT_FORM)),
                List.of(readable(DocumentCategory.ID_DOCUMENT)));

        assertThat(result.status()).isEqualTo(AggregateStatus.READY);
        assertThat(result.checks().get(1).status()).isEqualTo(CheckStatus.MISSING);
        assertThat(result.checks().get(1).mandatory()).isFalse();
    }

    @Test
    void case4UnreadableScanNeedsReviewNotMissing() {
        Evidence scan = new Evidence(UUID.randomUUID(), DocumentCategory.ID_DOCUMENT, DocumentStatus.NEEDS_REVIEW,
                ExtractionStatus.EXTRACTED, ReviewReason.LOW_CONFIDENCE, null, RECENT, false);

        var result = evaluate(List.of(mandatory(DocumentCategory.ID_DOCUMENT)), List.of(scan));

        assertThat(result.status()).isEqualTo(AggregateStatus.NEEDS_REVIEW);
        ReadinessCheck check = result.checks().get(0);
        assertThat(check.status()).isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(check.requiresHumanReview()).isTrue();
        assertThat(check.evidenceDocumentIds()).containsExactly(scan.documentId());
        assertThat(check.reason()).contains("LOW_CONFIDENCE");
    }

    @Test
    void case5ExpiredDocumentBlocksReadiness() {
        Evidence card = new Evidence(UUID.randomUUID(), DocumentCategory.INSURANCE_CARD, DocumentStatus.UPLOADED,
                ExtractionStatus.EXTRACTED, null, DAY.minusDays(1), RECENT, false);

        var result = evaluate(List.of(mandatory(DocumentCategory.INSURANCE_CARD)), List.of(card));

        assertThat(result.status()).isEqualTo(AggregateStatus.INCOMPLETE);
        assertThat(result.checks().get(0).status()).isEqualTo(CheckStatus.EXPIRED);
        assertThat(result.checks().get(0).evidenceDocumentIds()).containsExactly(card.documentId());
    }

    @Test
    void case5bValidityDaysAfterUploadAlsoExpire() {
        var rule = new Requirement(UUID.randomUUID(), Kind.DOCUMENT, Set.of(DocumentCategory.LAB_RESULT), true, 30, false, null);
        Evidence old = new Evidence(UUID.randomUUID(), DocumentCategory.LAB_RESULT, DocumentStatus.UPLOADED,
                ExtractionStatus.EXTRACTED, null, null, Instant.parse("2026-09-01T09:00:00Z"), false);

        assertThat(evaluate(List.of(rule), List.of(old)).checks().get(0).status()).isEqualTo(CheckStatus.EXPIRED);
        assertThat(evaluate(List.of(rule), List.of(readable(DocumentCategory.LAB_RESULT))).status())
                .isEqualTo(AggregateStatus.READY);
    }

    @Test
    void case6ConflictingDateNeedsReview() {
        Evidence prescription = new Evidence(UUID.randomUUID(), DocumentCategory.PRESCRIPTION, DocumentStatus.UPLOADED,
                ExtractionStatus.EXTRACTED, null, null, RECENT, true);

        var result = evaluate(List.of(mandatory(DocumentCategory.PRESCRIPTION)), List.of(prescription));

        assertThat(result.status()).isEqualTo(AggregateStatus.NEEDS_REVIEW);
        assertThat(result.checks().get(0).reason()).contains("conflicting dates");
    }

    @Test
    void case7ReRunningIsDeterministicSoFollowUpsCanBeDeduplicated() {
        var rules = List.of(mandatory(DocumentCategory.REFERRAL_LETTER));

        assertThat(evaluate(rules, List.of())).isEqualTo(evaluate(rules, List.of()));
    }

    @Test
    void case8ExtractionFailureNeedsReviewWithReason() {
        Evidence scan = new Evidence(UUID.randomUUID(), DocumentCategory.REFERRAL_LETTER, DocumentStatus.UPLOADED,
                ExtractionStatus.EXTRACTION_FAILED, null, null, RECENT, false);

        var result = evaluate(List.of(mandatory(DocumentCategory.REFERRAL_LETTER)), List.of(scan));

        assertThat(result.status()).isEqualTo(AggregateStatus.NEEDS_REVIEW);
        assertThat(result.checks().get(0).status()).isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(result.checks().get(0).reason()).contains("text extraction failed");
    }

    @Test
    void eitherAlternativeInsuranceDocumentSatisfiesTheRequirement() {
        var rule = mandatory(DocumentCategory.INSURANCE_CARD, DocumentCategory.OTHER);

        assertThat(evaluate(List.of(rule), List.of(readable(DocumentCategory.OTHER))).status()).isEqualTo(AggregateStatus.READY);
        assertThat(evaluate(List.of(rule), List.of(readable(DocumentCategory.INSURANCE_CARD))).status()).isEqualTo(AggregateStatus.READY);
    }

    @Test
    void missingAndUncertainTogetherAreIncompleteWithBothReasonsVisible() {
        Evidence failed = new Evidence(UUID.randomUUID(), DocumentCategory.ID_DOCUMENT, DocumentStatus.UPLOADED,
                ExtractionStatus.EXTRACTION_FAILED, null, null, RECENT, false);

        var result = evaluate(List.of(mandatory(DocumentCategory.ID_DOCUMENT), mandatory(DocumentCategory.REFERRAL_LETTER)),
                List.of(failed));

        assertThat(result.status()).isEqualTo(AggregateStatus.INCOMPLETE);
        assertThat(result.checks()).extracting(ReadinessCheck::status)
                .containsExactly(CheckStatus.NEEDS_REVIEW, CheckStatus.MISSING);
    }

    @Test
    void rejectedDocumentIsConfirmedNonComplianceNotPresent() {
        Evidence rejected = new Evidence(UUID.randomUUID(), DocumentCategory.CONSENT_FORM, DocumentStatus.REJECTED,
                ExtractionStatus.EXTRACTED, null, null, RECENT, false);

        var result = evaluate(List.of(mandatory(DocumentCategory.CONSENT_FORM)), List.of(rejected));

        assertThat(result.status()).isEqualTo(AggregateStatus.INCOMPLETE);
        assertThat(result.checks().get(0).status()).isEqualTo(CheckStatus.MISSING);
        assertThat(result.checks().get(0).reason()).contains("rejected");
        assertThat(result.checks().get(0).evidenceDocumentIds()).containsExactly(rejected.documentId());
    }

    @Test
    void pendingVerificationNeedsReviewUntilStaffVerifies() {
        var rule = new Requirement(UUID.randomUUID(), Kind.DOCUMENT, Set.of(DocumentCategory.ID_DOCUMENT), true, null, true, null);
        Evidence unverified = readable(DocumentCategory.ID_DOCUMENT);
        Evidence verified = new Evidence(UUID.randomUUID(), DocumentCategory.ID_DOCUMENT, DocumentStatus.VERIFIED,
                ExtractionStatus.EXTRACTION_FAILED, null, null, RECENT, false);

        assertThat(evaluate(List.of(rule), List.of(unverified)).status()).isEqualTo(AggregateStatus.NEEDS_REVIEW);
        assertThat(evaluate(List.of(rule), List.of(verified)).status()).isEqualTo(AggregateStatus.READY);
    }

    @Test
    void exemptionMakesRequirementNotApplicable() {
        var rule = new Requirement(UUID.randomUUID(), Kind.DOCUMENT, Set.of(DocumentCategory.REFERRAL_LETTER), true, null,
                false, "Follow-up visit: no referral needed");

        var result = evaluate(List.of(rule), List.of());

        assertThat(result.status()).isEqualTo(AggregateStatus.READY);
        assertThat(result.checks().get(0).status()).isEqualTo(CheckStatus.NOT_APPLICABLE);
    }

    @Test
    void undefinedExaminationListIsNeverComplete() {
        var rule = new Requirement(UUID.randomUUID(), Kind.EXPECTED_EXAMINATIONS, null, true, null, false, null);

        var undefined = ReadinessEngine.evaluate(List.of(rule), List.of(), new AppointmentFacts(DAY, TUNIS, null, null, false));
        var none = ReadinessEngine.evaluate(List.of(rule), List.of(), APPOINTMENT);

        assertThat(undefined.status()).isEqualTo(AggregateStatus.NEEDS_REVIEW);
        assertThat(none.status()).isEqualTo(AggregateStatus.READY);
    }

    @Test
    void pendingPaymentIsDistinctFromConfirmedPayment() {
        var rule = new Requirement(UUID.randomUUID(), Kind.ADVANCE_PAYMENT, null, true, null, false, null);
        var amount = new BigDecimal("150.500");

        var pending = ReadinessEngine.evaluate(List.of(rule), List.of(), new AppointmentFacts(DAY, TUNIS, List.of(), amount, false));
        var confirmed = ReadinessEngine.evaluate(List.of(rule), List.of(), new AppointmentFacts(DAY, TUNIS, List.of(), amount, true));

        assertThat(pending.checks().get(0).status()).isEqualTo(CheckStatus.NEEDS_REVIEW);
        assertThat(pending.checks().get(0).reason()).contains("pending");
        assertThat(confirmed.checks().get(0).status()).isEqualTo(CheckStatus.PRESENT);
    }
}
