package com.clinicready.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * The agent run the current thread is working for. {@link AuditService} stamps it on every event recorded
 * while it is set, so each change made during a run can be traced back to that run. The orchestrator sets it
 * around a run and always clears it.
 */
public final class AuditRunContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private AuditRunContext() {
    }

    public static void set(UUID runId) {
        CURRENT.set(runId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
