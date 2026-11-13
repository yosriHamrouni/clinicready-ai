export type Role = 'RECEPTIONIST' | 'PRACTITIONER' | 'CLINIC_MANAGER';

export interface CurrentUser {
  id: string;
  email: string;
  fullName: string;
  clinicId: string;
  roles: Role[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * ASSUMPTION (to confirm with backend): the session is carried by an httpOnly cookie,
 * so the response only needs `user`. `accessToken` is optional: if the backend returns a
 * JWT instead, the client keeps it in memory and sends it as a Bearer header.
 */
export interface LoginResponse {
  accessToken?: string;
  user: CurrentUser;
}

export type DocumentCategory =
  | 'ID_DOCUMENT'
  | 'INSURANCE_CARD'
  | 'PRESCRIPTION'
  | 'REFERRAL_LETTER'
  | 'CONSENT_FORM'
  | 'LAB_RESULT'
  | 'IMAGING_REPORT'
  | 'OTHER';

export const DOCUMENT_CATEGORIES: DocumentCategory[] = [
  'ID_DOCUMENT',
  'INSURANCE_CARD',
  'PRESCRIPTION',
  'REFERRAL_LETTER',
  'CONSENT_FORM',
  'LAB_RESULT',
  'IMAGING_REPORT',
  'OTHER',
];

export interface AppointmentType {
  id: string;
  code: string;
  label: string;
}

/** Body of POST /appointment-types/{id}/requirements and PUT /requirements/{id}. */
export interface RequirementRuleInput {
  documentCategory: DocumentCategory;
  mandatory: boolean;
  /** Days a document stays valid after upload; null = never expires. */
  validityDays: number | null;
  needsVerification: boolean;
}

export interface RequirementRule extends RequirementRuleInput {
  id: string;
  appointmentTypeId: string;
  updatedAt: string;
}

/** Error body shape we expect from the backend (all fields optional on parse). */
export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: unknown;
}
