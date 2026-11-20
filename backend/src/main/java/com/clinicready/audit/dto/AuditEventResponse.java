package com.clinicready.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.clinicready.audit.AuditAction;

/** One audit event. {@code actorName} is null for changes made by the system. */
public record AuditEventResponse(
        UUID id,
        AuditAction action,
        String entityType,
        UUID entityId,
        UUID actorUserId,
        String actorName,
        UUID runId,
        Map<String, Object> changeSummary,
        Instant occurredAt) {

    /** A page of events, newest first. */
    public record Page(List<AuditEventResponse> items, int page, int size, long total) {
    }
}
