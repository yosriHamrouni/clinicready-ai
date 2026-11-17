package com.clinicready.agent;

import java.util.List;
import java.util.UUID;

import com.clinicready.readiness.AggregateStatus;
import com.clinicready.readiness.CheckStatus;

/**
 * The agent's final answer: a short summary, counts per aggregate status and per-appointment findings. Counts
 * are computed here from the appointments, never taken from the model, and the whole report is cross-checked
 * against the stored readiness results before the run succeeds.
 */
public record AgentReport(String summary, Counts counts, List<AppointmentFinding> appointments) {

    public record Counts(int ready, int needsReview, int incomplete) {
    }

    public record AppointmentFinding(UUID appointmentId, AggregateStatus status, List<Finding> findings) {
    }

    public record Finding(String requirementCode, CheckStatus status, String action) {
    }

    /** What the model must return: the same, without counts. */
    record Draft(String summary, List<AppointmentFinding> appointments) {
    }

    static AgentReport of(Draft draft) {
        List<AppointmentFinding> list = draft.appointments() == null ? List.of() : draft.appointments();
        return new AgentReport(draft.summary(), new Counts(count(list, AggregateStatus.READY),
                count(list, AggregateStatus.NEEDS_REVIEW), count(list, AggregateStatus.INCOMPLETE)), list);
    }

    private static int count(List<AppointmentFinding> list, AggregateStatus status) {
        return (int) list.stream().filter(a -> a.status() == status).count();
    }
}
