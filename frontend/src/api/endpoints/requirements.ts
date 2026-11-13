import { del, get, post, put } from '../client';
import type { AppointmentType, RequirementRule, RequirementRuleInput } from '../types';

export const requirementsApi = {
  appointmentTypes: () => get<AppointmentType[]>('/appointment-types'),
  list: (appointmentTypeId: string) =>
    get<RequirementRule[]>(
      `/appointment-types/${encodeURIComponent(appointmentTypeId)}/requirements`,
    ),
  create: (appointmentTypeId: string, body: RequirementRuleInput) =>
    post<RequirementRule, RequirementRuleInput>(
      `/appointment-types/${encodeURIComponent(appointmentTypeId)}/requirements`,
      body,
    ),
  update: (id: string, body: RequirementRuleInput) =>
    put<RequirementRule, RequirementRuleInput>(`/requirements/${encodeURIComponent(id)}`, body),
  remove: (id: string) => del<void>(`/requirements/${encodeURIComponent(id)}`),
};
