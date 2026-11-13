import { del, get, post, put } from '../client';
import type {
  AdminDetailsInput,
  Appointment,
  AppointmentQuery,
  NewAppointmentInput,
  Practitioner,
} from '../types';

const path = (id: string) => `/appointments/${encodeURIComponent(id)}`;

function query(params: AppointmentQuery): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(key, value);
  }
  return search.toString();
}

export const appointmentsApi = {
  list: (params: AppointmentQuery) => get<Appointment[]>(`/appointments?${query(params)}`),
  get: (id: string) => get<Appointment>(path(id)),
  create: (body: NewAppointmentInput) =>
    post<Appointment, NewAppointmentInput>('/appointments', body),
  updateAdminDetails: (id: string, body: AdminDetailsInput) =>
    put<Appointment, AdminDetailsInput>(`${path(id)}/admin-details`, body),
  confirmTrustedContact: (id: string) =>
    post<Appointment>(`${path(id)}/trusted-contact-confirmation`),
  revokeTrustedContact: (id: string) =>
    del<Appointment>(`${path(id)}/trusted-contact-confirmation`),
  confirmPayment: (id: string) => post<Appointment>(`${path(id)}/payment-confirmation`),
  revokePayment: (id: string) => del<Appointment>(`${path(id)}/payment-confirmation`),
  practitioners: () => get<Practitioner[]>('/practitioners'),
};
