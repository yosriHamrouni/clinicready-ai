package com.clinicready.followup;

import java.util.Set;

/** Lifecycle of a follow-up task (PRD FR-E2). Transitions are enforced by {@link FollowUpService}. */
public enum FollowUpStatus {
    PROPOSED,
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    DELIVERED,
    RESOLVED;

    /** Statuses in which a task still stands for its requirement: a second one would be a duplicate. */
    public static final Set<FollowUpStatus> OPEN = Set.of(PROPOSED, AWAITING_APPROVAL, APPROVED, DELIVERED);
}
