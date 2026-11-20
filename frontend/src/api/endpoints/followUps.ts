import { get } from '../client';
import type { FollowUpStatus, FollowUpTask } from '../types';

export const followUpsApi = {
  list: (statuses: FollowUpStatus[] = []) =>
    get<FollowUpTask[]>(
      statuses.length ? `/follow-up-tasks?status=${statuses.join(',')}` : '/follow-up-tasks',
    ),
  get: (id: string) => get<FollowUpTask>(`/follow-up-tasks/${encodeURIComponent(id)}`),
};
