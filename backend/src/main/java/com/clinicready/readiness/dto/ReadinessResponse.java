package com.clinicready.readiness.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.clinicready.readiness.AggregateStatus;
import com.clinicready.readiness.CheckStatus;

/**
 * Latest readiness of one appointment. Blocking and review reasons are listed separately so a review
 * reason stays visible when the aggregate is {@code INCOMPLETE}.
 */
public record ReadinessResponse(
        UUID appointmentId,
        AggregateStatus status,
        String notice,
        Instant evaluatedAt,
        List<String> incompleteReasons,
        List<String> reviewReasons,
        List<ReadinessCheckResponse> checks) {

    public static final String NOTICE = "Administrative completeness check only. "
            + "It is not a medical decision and does not guarantee coverage or payment.";

    public static ReadinessResponse of(UUID appointmentId, AggregateStatus status, Instant evaluatedAt,
            List<ReadinessCheckResponse> checks) {
        return new ReadinessResponse(appointmentId, status, NOTICE, evaluatedAt,
                reasons(checks, CheckStatus.MISSING, CheckStatus.EXPIRED),
                reasons(checks, CheckStatus.NEEDS_REVIEW), checks);
    }

    private static List<String> reasons(List<ReadinessCheckResponse> checks, CheckStatus... statuses) {
        List<CheckStatus> wanted = List.of(statuses);
        return checks.stream()
                .filter(c -> c.mandatory() && wanted.contains(c.status()))
                .map(ReadinessCheckResponse::reason)
                .toList();
    }
}
