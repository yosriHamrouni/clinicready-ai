import { http, HttpResponse } from 'msw';
import type { CurrentUser } from '../api/types';

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
];
