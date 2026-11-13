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

export type Language = 'FR' | 'AR';

export interface Patient {
  id: string;
  patientRef: string;
  fullName: string;
  phone: string | null;
  preferredLanguage: Language;
}

/** Body of POST /patients. patientRef is generated when omitted. */
export interface NewPatientInput {
  patientRef?: string;
  fullName: string;
  phone?: string;
  preferredLanguage?: Language;
}

export interface Practitioner {
  id: string;
  fullName: string;
  specialty: string | null;
}

export type AppointmentStatus = 'SCHEDULED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

export const APPOINTMENT_STATUSES: AppointmentStatus[] = [
  'SCHEDULED',
  'COMPLETED',
  'CANCELLED',
  'NO_SHOW',
];

export type ContactMethod = 'PHONE' | 'EMAIL';

/** MISSING = not provided; PROVIDED = entered by staff; CONFIRMED = also confirmed by staff. */
export type ChecklistItemStatus = 'MISSING' | 'PROVIDED' | 'CONFIRMED';

export interface Confirmation {
  /** Id of the staff member who confirmed. */
  by: string;
  at: string;
}

export interface AdminDetails {
  trustedContact: {
    status: ChecklistItemStatus;
    name: string | null;
    method: ContactMethod | null;
    value: string | null;
    confirmation: Confirmation | null;
  };
  expectedExaminations: { status: ChecklistItemStatus; items: string[] | null };
  advance: {
    status: ChecklistItemStatus;
    amount: number | null;
    currency: string;
    paymentConfirmation: Confirmation | null;
  };
}

export interface Appointment {
  id: string;
  startsAt: string;
  status: AppointmentStatus;
  updatedAt: string;
  patient: { id: string; patientRef: string; fullName: string };
  practitioner: { id: string; fullName: string };
  appointmentType: AppointmentType;
  adminDetails: AdminDetails;
}

/** Body of POST /appointments. */
export interface NewAppointmentInput {
  patientId: string;
  practitionerId: string;
  appointmentTypeId: string;
  startsAt: string;
}

/** Body of PUT /appointments/{id}/admin-details. null = not provided. */
export interface AdminDetailsInput {
  trustedContact: { name: string; method: ContactMethod; value: string } | null;
  expectedExaminations: string[] | null;
  advanceAmount: number | null;
}

export interface AppointmentQuery {
  from?: string;
  to?: string;
  practitionerId?: string;
  patientId?: string;
  status?: AppointmentStatus;
}

/** Error body shape we expect from the backend (all fields optional on parse). */
export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: unknown;
}
