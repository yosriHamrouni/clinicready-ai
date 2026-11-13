package com.clinicready.audit;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.security.CurrentUserService;

/**
 * Writes audit events in the caller's transaction, so a change and its audit event commit or roll
 * back together. The actor and clinic come from the current user, never from the caller.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final CurrentUserService currentUser;

    AuditService(AuditEventRepository events, CurrentUserService currentUser) {
        this.events = events;
        this.currentUser = currentUser;
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
                entityId, changeSummary));
    }
}
