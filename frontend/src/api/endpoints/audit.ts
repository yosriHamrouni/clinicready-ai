import { get } from '../client';
import type { AuditEventPage } from '../types';

export interface AuditFilter {
  runId?: string;
  entityType?: string;
  page?: number;
  size?: number;
}

export const auditApi = {
  list: ({ runId, entityType, page = 0, size = 25 }: AuditFilter = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (runId) params.set('runId', runId);
    if (entityType) params.set('entityType', entityType);
    return get<AuditEventPage>(`/audit-events?${params.toString()}`);
  },
};
