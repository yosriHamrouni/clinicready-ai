package com.clinicready.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.common.InvalidRequestException;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;
import com.clinicready.security.Role;

/**
 * Writes audit events in the caller's transaction, so a change and its audit event commit or roll
 * back together, and reads them back. The actor and clinic come from the current user, never from the caller.
 * An event recorded while {@link AuditRunContext} is set carries that run's id.
 */
@Service
public class AuditService {

    static final int MAX_PAGE_SIZE = 100;

    /** Narrowing options for {@link #search}; every field is optional. */
    public record Filter(UUID runId, String entityType, UUID entityId, Instant from, Instant to) {
    }

    private final AuditEventRepository events;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;

    AuditService(AuditEventRepository events, CurrentUserService currentUser, AuthorizationService authorization) {
        this.events = events;
        this.currentUser = currentUser;
        this.authorization = authorization;
    }

    /**
     * Records a change made by the current user.
     *
     * @param changeSummary ids and field values only; never document text or patient identity details
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(AuditAction action, String entityType, UUID entityId,
            Map<String, Object> changeSummary) {
        return events.save(new AuditEvent(currentUser.clinicId(), currentUser.userId(), action, entityType,
                entityId, changeSummary, AuditRunContext.current().orElse(null)));
    }

    /**
     * Records an event about an agent run itself. Commits on its own, so the run's trail is kept even when
     * the run is not inside a transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordRunEvent(AuditAction action, UUID runId, Map<String, Object> changeSummary) {
        return events.save(new AuditEvent(currentUser.clinicId(), currentUser.userId(), action, "AGENT_RUN", runId,
                changeSummary, runId));
    }

    /**
     * The clinic's audit events, newest first. A clinic manager sees every event; a receptionist only the
     * events of their own actions. Roles without {@link Permission#AUDIT_READ} get a 403.
     *
     * @throws InvalidRequestException {@code from} after {@code to}
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> search(Filter filter, int page, int size) {
        authorization.require(Permission.AUDIT_READ);
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException("'from' must not be after 'to'");
        }
        List<Specification<AuditEvent>> parts = new ArrayList<>();
        parts.add((e, q, cb) -> cb.equal(e.get("clinicId"), currentUser.clinicId()));
        if (currentUser.role() != Role.CLINIC_MANAGER) {
            parts.add((e, q, cb) -> cb.equal(e.get("actorUserId"), currentUser.userId()));
        }
        if (filter.runId() != null) {
            parts.add((e, q, cb) -> cb.equal(e.get("runId"), filter.runId()));
        }
        if (filter.entityType() != null && !filter.entityType().isBlank()) {
            parts.add((e, q, cb) -> cb.equal(e.get("entityType"), filter.entityType().strip()));
        }
        if (filter.entityId() != null) {
            parts.add((e, q, cb) -> cb.equal(e.get("entityId"), filter.entityId()));
        }
        if (filter.from() != null) {
            parts.add((e, q, cb) -> cb.greaterThanOrEqualTo(e.get("occurredAt"), filter.from()));
        }
        if (filter.to() != null) {
            parts.add((e, q, cb) -> cb.lessThanOrEqualTo(e.get("occurredAt"), filter.to()));
        }
        PageRequest paging = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return events.findAll(Specification.allOf(parts), paging);
    }
}
