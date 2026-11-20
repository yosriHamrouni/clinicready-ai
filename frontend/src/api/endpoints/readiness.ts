import { ApiError, get, post } from '../client';
import type { Readiness } from '../types';

export const readinessKey = (appointmentId: string) => ['readiness', appointmentId] as const;

const path = (id: string) => `/appointments/${encodeURIComponent(id)}`;

export const readinessApi = {
  /** Latest stored result; null when no check has run yet (the API answers 404). */
  latest: async (appointmentId: string): Promise<Readiness | null> => {
    try {
      return await get<Readiness>(`${path(appointmentId)}/readiness`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) return null;
      throw error;
    }
  },
  run: (appointmentId: string) => post<Readiness>(`${path(appointmentId)}/readiness-checks`),
};
