import { http, HttpResponse } from 'msw';
import type {
  AdminDetails,
  AdminDetailsInput,
  Appointment,
  AppointmentType,
  CurrentUser,
  NewAppointmentInput,
  NewPatientInput,
  Patient,
  Practitioner,
  RequirementRule,
  RequirementRuleInput,
} from '../api/types';

// Synthetic data only.
export const MOCK_USER: CurrentUser = {
  id: 'user-001',
  email: 'reception@clinic.test',
  fullName: 'Sarra Test',
  clinicId: 'clinic-001',
  roles: ['RECEPTIONIST'],
};

const SESSION_KEY = 'mock-session';

function hasSession(): boolean {
  try {
    return sessionStorage.getItem(SESSION_KEY) === '1';
  } catch {
    return false;
  }
}

function setSession(on: boolean): void {
  try {
    if (on) sessionStorage.setItem(SESSION_KEY, '1');
    else sessionStorage.removeItem(SESSION_KEY);
  } catch {
    // ignore
  }
}

export const MOCK_APPOINTMENT_TYPES: AppointmentType[] = [
  { id: 'type-consult', code: 'CONSULT', label: 'Consultation' },
  { id: 'type-mri', code: 'MRI', label: 'IRM' },
];

const initialRules = (): RequirementRule[] => [
  {
    id: 'rule-001',
    appointmentTypeId: 'type-mri',
    documentCategory: 'CONSENT_FORM',
    mandatory: true,
    validityDays: 30,
    needsVerification: true,
    updatedAt: '2026-11-01T09:00:00Z',
  },
];

let rules = initialRules();

/** Restores the mock rules (tests). */
export function resetMockRules(): void {
  rules = initialRules();
}

const notFound = () =>
  HttpResponse.json({ code: 'NOT_FOUND', message: 'Not found' }, { status: 404 });

const conflict = () =>
  HttpResponse.json(
    { code: 'RULE_CONFLICT', message: 'Rule already exists for this category' },
    { status: 409 },
  );

export const MOCK_PRACTITIONERS: Practitioner[] = [
  { id: 'prac-001', fullName: 'Dr Karim Test', specialty: 'Cardiologie' },
  { id: 'prac-002', fullName: 'Dr Leila Test', specialty: 'Radiologie' },
];

const initialPatients = (): Patient[] => [
  {
    id: 'patient-001',
    patientRef: 'P-0001',
    fullName: 'Amel Test',
    phone: '+216 20 000 001',
    preferredLanguage: 'AR',
  },
  {
    id: 'patient-002',
    patientRef: 'P-0002',
    fullName: 'Bechir Test',
    phone: null,
    preferredLanguage: 'FR',
  },
];

const emptyAdminDetails = (): AdminDetails => ({
  trustedContact: { status: 'MISSING', name: null, method: null, value: null, confirmation: null },
  expectedExaminations: { status: 'MISSING', items: null },
  advance: { status: 'MISSING', amount: null, currency: 'TND', paymentConfirmation: null },
});

/** Local 09:00 / 14:30 today, so the default day view of the calendar shows them. */
function todayAt(hours: number, minutes = 0): string {
  const d = new Date();
  d.setHours(hours, minutes, 0, 0);
  return d.toISOString();
}

function mockAppointment(
  id: string,
  patient: Patient,
  practitioner: Practitioner,
  type: AppointmentType,
  startsAt: string,
  status: Appointment['status'] = 'SCHEDULED',
): Appointment {
  return {
    id,
    startsAt,
    status,
    updatedAt: '2026-11-01T09:00:00Z',
    patient: { id: patient.id, patientRef: patient.patientRef, fullName: patient.fullName },
    practitioner: { id: practitioner.id, fullName: practitioner.fullName },
    appointmentType: type,
    adminDetails: emptyAdminDetails(),
  };
}

let patients = initialPatients();

const initialAppointments = (): Appointment[] => [
  mockAppointment(
    'appt-001',
    patients[0]!,
    MOCK_PRACTITIONERS[0]!,
    MOCK_APPOINTMENT_TYPES[0]!,
    todayAt(9),
  ),
  mockAppointment(
    'appt-002',
    patients[1]!,
    MOCK_PRACTITIONERS[1]!,
    MOCK_APPOINTMENT_TYPES[1]!,
    todayAt(14, 30),
    'CANCELLED',
  ),
];

let appointments = initialAppointments();

/** Restores the mock patients and appointments (tests). */
export function resetMockClinicData(): void {
  patients = initialPatients();
  appointments = initialAppointments();
}

function sameContact(a: AdminDetails['trustedContact'], b: AdminDetailsInput['trustedContact']) {
  return b ? a.name === b.name && a.method === b.method && a.value === b.value : a.name === null;
}

/** Mirrors the backend: a changed contact or amount drops its confirmation. */
function applyAdminDetails(current: AdminDetails, input: AdminDetailsInput): AdminDetails {
  const contactConfirmation = sameContact(current.trustedContact, input.trustedContact)
    ? current.trustedContact.confirmation
    : null;
  const paymentConfirmation =
    current.advance.amount === input.advanceAmount ? current.advance.paymentConfirmation : null;
  return {
    trustedContact: {
      status: !input.trustedContact ? 'MISSING' : contactConfirmation ? 'CONFIRMED' : 'PROVIDED',
      name: input.trustedContact?.name ?? null,
      method: input.trustedContact?.method ?? null,
      value: input.trustedContact?.value ?? null,
      confirmation: contactConfirmation,
    },
    expectedExaminations: {
      status: input.expectedExaminations ? 'PROVIDED' : 'MISSING',
      items: input.expectedExaminations,
    },
    advance: {
      status:
        input.advanceAmount == null ? 'MISSING' : paymentConfirmation ? 'CONFIRMED' : 'PROVIDED',
      amount: input.advanceAmount,
      currency: 'TND',
      paymentConfirmation,
    },
  };
}

function updateAppointment(id: string, change: (a: Appointment) => Appointment | Response) {
  const existing = appointments.find((a) => a.id === id);
  if (!existing) return notFound();
  const updated = change(existing);
  if (updated instanceof Response) return updated;
  const next = { ...updated, updatedAt: new Date().toISOString() };
  appointments = appointments.map((a) => (a.id === id ? next : a));
  return HttpResponse.json(next);
}

const confirmation = () => ({ by: MOCK_USER.id, at: new Date().toISOString() });

function takenCategory(typeId: string, body: RequirementRuleInput, exceptId?: string): boolean {
  return rules.some(
    (r) =>
      r.appointmentTypeId === typeId &&
      r.documentCategory === body.documentCategory &&
      r.id !== exceptId,
  );
}

// Wildcard prefix keeps handlers independent of VITE_API_BASE_URL.
export const handlers = [
  http.post('*/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    if (body.email === MOCK_USER.email && body.password === 'password') {
      setSession(true);
      return HttpResponse.json({ user: MOCK_USER });
    }
    return HttpResponse.json(
      { code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' },
      { status: 401 },
    );
  }),

  http.get('*/auth/me', () => {
    if (!hasSession()) {
      return HttpResponse.json(
        { code: 'UNAUTHENTICATED', message: 'Not authenticated' },
        { status: 401 },
      );
    }
    return HttpResponse.json(MOCK_USER);
  }),

  http.post('*/auth/logout', () => {
    setSession(false);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/appointment-types', () => HttpResponse.json(MOCK_APPOINTMENT_TYPES)),

  http.get('*/appointment-types/:typeId/requirements', ({ params }) => {
    if (!MOCK_APPOINTMENT_TYPES.some((t) => t.id === params.typeId)) return notFound();
    return HttpResponse.json(rules.filter((r) => r.appointmentTypeId === params.typeId));
  }),

  http.post('*/appointment-types/:typeId/requirements', async ({ params, request }) => {
    const typeId = String(params.typeId);
    if (!MOCK_APPOINTMENT_TYPES.some((t) => t.id === typeId)) return notFound();
    const body = (await request.json()) as RequirementRuleInput;
    if (takenCategory(typeId, body)) return conflict();
    const rule: RequirementRule = {
      ...body,
      id: `rule-${Date.now()}-${rules.length}`,
      appointmentTypeId: typeId,
      updatedAt: new Date().toISOString(),
    };
    rules.push(rule);
    return HttpResponse.json(rule, { status: 201 });
  }),

  http.put('*/requirements/:id', async ({ params, request }) => {
    const existing = rules.find((r) => r.id === params.id);
    if (!existing) return notFound();
    const body = (await request.json()) as RequirementRuleInput;
    if (takenCategory(existing.appointmentTypeId, body, existing.id)) return conflict();
    const updated: RequirementRule = { ...existing, ...body, updatedAt: new Date().toISOString() };
    rules = rules.map((r) => (r.id === existing.id ? updated : r));
    return HttpResponse.json(updated);
  }),

  http.delete('*/requirements/:id', ({ params }) => {
    if (!rules.some((r) => r.id === params.id)) return notFound();
    rules = rules.filter((r) => r.id !== params.id);
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('*/patients', ({ request }) => {
    const q = (new URL(request.url).searchParams.get('q') ?? '').toLowerCase();
    return HttpResponse.json(
      patients.filter(
        (p) => p.fullName.toLowerCase().includes(q) || p.patientRef.toLowerCase().includes(q),
      ),
    );
  }),

  http.get('*/patients/:id', ({ params }) => {
    const patient = patients.find((p) => p.id === params.id);
    return patient ? HttpResponse.json(patient) : notFound();
  }),

  http.post('*/patients', async ({ request }) => {
    const body = (await request.json()) as NewPatientInput;
    const patientRef = body.patientRef ?? `P-${String(patients.length + 1).padStart(4, '0')}`;
    if (patients.some((p) => p.patientRef === patientRef)) {
      return HttpResponse.json(
        { code: 'PATIENT_REF_CONFLICT', message: 'Reference already used' },
        { status: 409 },
      );
    }
    const patient: Patient = {
      id: `patient-${Date.now()}`,
      patientRef,
      fullName: body.fullName,
      phone: body.phone ?? null,
      preferredLanguage: body.preferredLanguage ?? 'FR',
    };
    patients.push(patient);
    return HttpResponse.json(patient, { status: 201 });
  }),

  http.get('*/practitioners', () => HttpResponse.json(MOCK_PRACTITIONERS)),

  http.get('*/appointments', ({ request }) => {
    const params = new URL(request.url).searchParams;
    const from = params.get('from');
    const to = params.get('to');
    const practitionerId = params.get('practitionerId');
    const patientId = params.get('patientId');
    const status = params.get('status');
    if (!patientId && !(from && to)) {
      return HttpResponse.json(
        { code: 'VALIDATION_ERROR', message: 'Give a date range or a patientId' },
        { status: 400 },
      );
    }
    return HttpResponse.json(
      appointments
        .filter((a) => !from || a.startsAt >= new Date(from).toISOString())
        .filter((a) => !to || a.startsAt < new Date(to).toISOString())
        .filter((a) => !practitionerId || a.practitioner.id === practitionerId)
        .filter((a) => !patientId || a.patient.id === patientId)
        .filter((a) => !status || status.split(',').includes(a.status))
        .sort((a, b) => a.startsAt.localeCompare(b.startsAt)),
    );
  }),

  http.get('*/appointments/:id', ({ params }) => {
    const appointment = appointments.find((a) => a.id === params.id);
    return appointment ? HttpResponse.json(appointment) : notFound();
  }),

  http.post('*/appointments', async ({ request }) => {
    const body = (await request.json()) as NewAppointmentInput;
    const patient = patients.find((p) => p.id === body.patientId);
    const practitioner = MOCK_PRACTITIONERS.find((p) => p.id === body.practitionerId);
    const type = MOCK_APPOINTMENT_TYPES.find((ty) => ty.id === body.appointmentTypeId);
    if (!patient || !practitioner || !type) return notFound();
    const appointment = mockAppointment(
      `appt-${Date.now()}`,
      patient,
      practitioner,
      type,
      body.startsAt,
    );
    appointments.push(appointment);
    return HttpResponse.json(appointment, { status: 201 });
  }),

  http.put('*/appointments/:id/admin-details', async ({ params, request }) => {
    const body = (await request.json()) as AdminDetailsInput;
    return updateAppointment(String(params.id), (a) => ({
      ...a,
      adminDetails: applyAdminDetails(a.adminDetails, body),
    }));
  }),

  http.post('*/appointments/:id/trusted-contact-confirmation', ({ params }) =>
    updateAppointment(String(params.id), (a) => {
      if (!a.adminDetails.trustedContact.name) {
        return HttpResponse.json(
          { code: 'TRUSTED_CONTACT_MISSING', message: 'No contact' },
          { status: 409 },
        );
      }
      const trustedContact = {
        ...a.adminDetails.trustedContact,
        status: 'CONFIRMED' as const,
        confirmation: a.adminDetails.trustedContact.confirmation ?? confirmation(),
      };
      return { ...a, adminDetails: { ...a.adminDetails, trustedContact } };
    }),
  ),

  http.delete('*/appointments/:id/trusted-contact-confirmation', ({ params }) =>
    updateAppointment(String(params.id), (a) => {
      const contact = a.adminDetails.trustedContact;
      const trustedContact = {
        ...contact,
        status: contact.name ? ('PROVIDED' as const) : ('MISSING' as const),
        confirmation: null,
      };
      return { ...a, adminDetails: { ...a.adminDetails, trustedContact } };
    }),
  ),

  http.post('*/appointments/:id/payment-confirmation', ({ params }) =>
    updateAppointment(String(params.id), (a) => {
      if (a.adminDetails.advance.amount == null) {
        return HttpResponse.json(
          { code: 'ADVANCE_AMOUNT_MISSING', message: 'No advance' },
          { status: 409 },
        );
      }
      const advance = {
        ...a.adminDetails.advance,
        status: 'CONFIRMED' as const,
        paymentConfirmation: a.adminDetails.advance.paymentConfirmation ?? confirmation(),
      };
      return { ...a, adminDetails: { ...a.adminDetails, advance } };
    }),
  ),

  http.delete('*/appointments/:id/payment-confirmation', ({ params }) =>
    updateAppointment(String(params.id), (a) => {
      const advance = {
        ...a.adminDetails.advance,
        status:
          a.adminDetails.advance.amount == null ? ('MISSING' as const) : ('PROVIDED' as const),
        paymentConfirmation: null,
      };
      return { ...a, adminDetails: { ...a.adminDetails, advance } };
    }),
  ),
];
