import { get, post } from '../client';
import type { NewPatientInput, Patient } from '../types';

export const patientsApi = {
  list: (q?: string) =>
    get<Patient[]>(q ? `/patients?${new URLSearchParams({ q }).toString()}` : '/patients'),
  get: (id: string) => get<Patient>(`/patients/${encodeURIComponent(id)}`),
  create: (body: NewPatientInput) => post<Patient, NewPatientInput>('/patients', body),
};
