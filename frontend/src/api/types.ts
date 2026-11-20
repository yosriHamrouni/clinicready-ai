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

export type DocumentSource = 'STAFF_UPLOAD' | 'EMAIL' | 'SCAN' | 'PATIENT_SUBMISSION';
export type DocumentStatus = 'UPLOADED' | 'NEEDS_REVIEW' | 'VERIFIED' | 'REJECTED';
export type ExtractionStatus = 'PENDING' | 'EXTRACTED' | 'EXTRACTION_FAILED';

/** An uploaded file of a patient (metadata only; the bytes come from GET /documents/{id}/content). */
export interface PatientDocument {
  id: string;
  patientId: string;
  category: DocumentCategory;
  source: DocumentSource;
  status: DocumentStatus;
  version: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  uploadedAt: string;
  extractionStatus: ExtractionStatus;
}

/** Editable fields of a document, as named by the API. */
export const METADATA_FIELDS = [
  'documentDate',
  'expiryDate',
  'issuer',
  'documentNumber',
  'holderName',
] as const;
export type MetadataFieldKey = (typeof METADATA_FIELDS)[number];
export type MetadataFields = Partial<Record<MetadataFieldKey, string>>;

/** GET/PATCH /documents/{id}/metadata. The editor is null until someone saves a field. */
export interface DocumentMetadata {
  documentId: string;
  fields: MetadataFields;
  updatedBy: string | null;
  updatedByName: string | null;
  updatedAt: string | null;
}

/** Body of PATCH /documents/{id}/metadata: an empty string or null clears a field. */
export interface MetadataPatch {
  fields: Partial<Record<MetadataFieldKey, string | null>>;
}

export type AggregateStatus = 'READY' | 'INCOMPLETE' | 'NEEDS_REVIEW';
export type CheckStatus = 'PRESENT' | 'MISSING' | 'NEEDS_REVIEW' | 'EXPIRED' | 'NOT_APPLICABLE';
export type RequirementKind = 'DOCUMENT' | 'EXPECTED_EXAMINATIONS' | 'ADVANCE_PAYMENT';

/** One requirement of GET /appointments/{id}/readiness. */
export interface ReadinessCheck {
  ruleId: string | null;
  requirement: RequirementKind;
  documentCategories: DocumentCategory[];
  mandatory: boolean;
  applicable: boolean;
  status: CheckStatus;
  reason: string;
  remainingAction: string | null;
  requiresHumanReview: boolean;
  documents: {
    documentId: string;
    category: DocumentCategory;
    version: number;
    status: DocumentStatus;
    uploadedAt: string;
  }[];
  confirmations: {
    type: string;
    confirmedBy: string;
    confirmedByName: string | null;
    confirmedAt: string;
  }[];
}

/** GET /appointments/{id}/readiness (404 until a check has run). */
export interface Readiness {
  appointmentId: string;
  status: AggregateStatus;
  notice: string;
  evaluatedAt: string;
  incompleteReasons: string[];
  reviewReasons: string[];
  checks: ReadinessCheck[];
}

export type FollowUpStatus =
  'PROPOSED' | 'AWAITING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'DELIVERED' | 'RESOLVED';

export const FOLLOW_UP_STATUSES: FollowUpStatus[] = [
  'PROPOSED',
  'AWAITING_APPROVAL',
  'APPROVED',
  'REJECTED',
  'DELIVERED',
  'RESOLVED',
];

/** Waiting on staff: not yet delivered, not closed. */
export const PENDING_FOLLOW_UP_STATUSES: FollowUpStatus[] = [
  'PROPOSED',
  'AWAITING_APPROVAL',
  'APPROVED',
];

export interface FollowUpApproval {
  id: string;
  decision: 'APPROVED' | 'REJECTED';
  actorId: string;
  comment: string | null;
  decidedAt: string;
}

/** A follow-up task. `approvals` is filled on single-task reads and empty in lists. */
export interface FollowUpTask {
  id: string;
  appointmentId: string;
  patientId: string;
  requirementCode: string;
  status: FollowUpStatus;
  checkStatus: CheckStatus;
  reason: string;
  suggestedAction: string;
  evidenceDocumentIds: string[];
  language: Language;
  dueAt: string | null;
  createdBy: string | null;
  runId: string | null;
  approvalId: string | null;
  deliveredAt: string | null;
  deliveredBy: string | null;
  deliveredByRun: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  approvals: FollowUpApproval[];
}

export type AgentRunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'TOOL_LIMIT_EXCEEDED'
  | 'TIMED_OUT'
  | 'REPORT_MISMATCH';

export const FINISHED_RUN_STATUSES: AgentRunStatus[] = [
  'SUCCEEDED',
  'FAILED',
  'TOOL_LIMIT_EXCEEDED',
  'TIMED_OUT',
  'REPORT_MISMATCH',
];

/** The agent's final answer; counts come from the server, never from the model. */
export interface AgentReport {
  summary: string;
  counts: { ready: number; needsReview: number; incomplete: number };
  appointments: {
    appointmentId: string;
    status: AggregateStatus;
    findings: { requirementCode: string; status: CheckStatus; action: string | null }[];
  }[];
}

/** One tool call of a run, in order. */
export interface AgentAction {
  seq: number;
  tool: string;
  arguments: Record<string, unknown>;
  resultStatus: string;
  latencyMs: number;
  tokens: number;
  at: string;
}

export interface AgentRun {
  id: string;
  request: string;
  status: AgentRunStatus;
  statusReason: string | null;
  report: AgentReport | null;
  toolCalls: number;
  promptTokens: number;
  completionTokens: number;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  actions: AgentAction[];
}

/** One recorded change. `actorName` is null for changes made by the system. */
export interface AuditEvent {
  id: string;
  action: string;
  entityType: string;
  entityId: string;
  actorUserId: string | null;
  actorName: string | null;
  runId: string | null;
  /** Ids and field values only; never document text or patient identity details. */
  changeSummary: Record<string, unknown>;
  occurredAt: string;
}

export interface AuditEventPage {
  items: AuditEvent[];
  page: number;
  size: number;
  total: number;
}
