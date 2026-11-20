package com.clinicready.audit;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.audit.dto.AuditEventResponse;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.UserDirectory;

/** Read-only audit trail. Who sees what is decided in {@link AuditService}. */
@RestController
@RequestMapping("/api/v1/audit-events")
class AuditController {

    private final AuditService audit;
    private final UserDirectory users;
    private final CurrentUserService currentUser;

    AuditController(AuditService audit, UserDirectory users, CurrentUserService currentUser) {
        this.audit = audit;
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping
    AuditEventResponse.Page list(@RequestParam(required = false) UUID runId,
            @RequestParam(required = false) String entityType, @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        var found = audit.search(new AuditService.Filter(runId, entityType, entityId, from, to), page, size);
        UUID clinicId = currentUser.clinicId();
        Map<UUID, Optional<String>> names = new HashMap<>();
        var items = found.getContent().stream().map(e -> {
            String name = e.getActorUserId() == null ? null
                    : names.computeIfAbsent(e.getActorUserId(), id -> users.fullName(id, clinicId)).orElse(null);
            return new AuditEventResponse(e.getId(), e.getAction(), e.getEntityType(), e.getEntityId(),
                    e.getActorUserId(), name, e.getRunId(), e.getChangeSummary(), e.getOccurredAt());
        }).toList();
        return new AuditEventResponse.Page(items, found.getNumber(), found.getSize(), found.getTotalElements());
    }
}
