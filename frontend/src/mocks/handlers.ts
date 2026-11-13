import { http, HttpResponse } from 'msw';
import type {
  AppointmentType,
  CurrentUser,
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
];
