package com.clinicready.audit;

/** What happened. One value per kind of state change; more are added as features start writing events. */
public enum AuditAction {
    REQUIREMENT_RULE_CREATED,
    REQUIREMENT_RULE_UPDATED,
    REQUIREMENT_RULE_DELETED
}
