import { get, post } from '../client';
import type { AgentRun, AgentRunStatus } from '../types';

export const agentRunsApi = {
  start: (request: string) =>
    post<{ runId: string; status: AgentRunStatus }, { request: string }>('/agent-runs', {
      request,
    }),
  get: (id: string) => get<AgentRun>(`/agent-runs/${encodeURIComponent(id)}`),
};
