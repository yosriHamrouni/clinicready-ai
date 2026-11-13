/**
 * Audit trail for agent runs, tool calls, approvals and state changes. {@link com.clinicready.audit.AuditService}
 * is the only writer; services call it in the same transaction as the change.
 */
package com.clinicready.audit;
