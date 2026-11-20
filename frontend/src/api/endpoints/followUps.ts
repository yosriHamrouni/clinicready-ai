import { get, post, put } from '../client';
import type { FollowUpStatus, FollowUpTask, MessageDraft } from '../types';

const task = (id: string) => `/follow-up-tasks/${encodeURIComponent(id)}`;

export const followUpsApi = {
  /** No statuses: the server's default, every open task. */
  list: (statuses: FollowUpStatus[] = []) =>
    get<FollowUpTask[]>(
      statuses.length ? `/follow-up-tasks?status=${statuses.join(',')}` : '/follow-up-tasks',
    ),
  get: (id: string) => get<FollowUpTask>(task(id)),
  draft: (id: string) => get<MessageDraft>(`${task(id)}/draft`),
  /** Only while the task is AWAITING_APPROVAL; otherwise 409 DRAFT_LOCKED. */
  saveDraft: (id: string, body: string) => put<MessageDraft, { body: string }>(`${task(id)}/draft`, { body }),
  approve: (id: string, comment?: string) =>
    post<FollowUpTask, { comment?: string }>(`${task(id)}/approve`, comment ? { comment } : {}),
  /** The comment is required. */
  reject: (id: string, comment: string) =>
    post<FollowUpTask, { comment: string }>(`${task(id)}/reject`, { comment }),
  /** Staff sent the message themselves (the product never sends anything). */
  markDelivered: (id: string) => post<FollowUpTask>(`${task(id)}/delivered`),
  resolve: (id: string) => post<FollowUpTask>(`${task(id)}/resolve`),
};
