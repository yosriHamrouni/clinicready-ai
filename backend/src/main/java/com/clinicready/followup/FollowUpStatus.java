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

    /** PROPOSED to AWAITING_APPROVAL to APPROVED or REJECTED; APPROVED to DELIVERED to RESOLVED. Nothing else. */
    public boolean canMoveTo(FollowUpStatus next) {
        return switch (this) {
            case PROPOSED -> next == AWAITING_APPROVAL;
            case AWAITING_APPROVAL -> next == APPROVED || next == REJECTED;
            case APPROVED -> next == DELIVERED;
            case DELIVERED -> next == RESOLVED;
            case REJECTED, RESOLVED -> false;
        };
    }
}
