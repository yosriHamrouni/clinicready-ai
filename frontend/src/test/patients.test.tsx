import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import type { NewPatientInput } from '../api/types';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.patients;

function renderAt(path: string, roles: (typeof MOCK_USER)['roles'] = ['RECEPTIONIST']) {
  server.use(http.get('*/auth/me', () => HttpResponse.json({ ...MOCK_USER, roles })));
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

beforeEach(() => resetMockClinicData());

describe('patients', () => {
  it('lists patients and filters them by search', async () => {
    renderAt('/patients');

    expect(await screen.findByText('Amel Test')).toBeInTheDocument();
    expect(screen.getByText('Bechir Test')).toBeInTheDocument();

    await userEvent.setup().type(screen.getByLabelText(t.search), 'bech');

    await waitFor(() => {
      expect(screen.queryByText('Amel Test')).not.toBeInTheDocument();
      expect(screen.getByText('Bechir Test')).toBeInTheDocument();
    });
  });

  it('creates a patient and opens their record', async () => {
    let sent: NewPatientInput | undefined;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/patients')) {
        sent = (await request.clone().json()) as NewPatientInput;
      }
    });
    const router = renderAt('/patients');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: t.add }));
    const form = screen.getByRole('form', { name: t.addTitle });
    await user.type(within(form).getByLabelText(t.fullName), '  Chiraz Test ');
    await user.type(within(form).getByLabelText(t.phone), 'abc');
    expect(within(form).getByText(t.phoneInvalid)).toBeInTheDocument();
    expect(within(form).getByRole('button', { name: t.create })).toBeDisabled();

    await user.clear(within(form).getByLabelText(t.phone));
    await user.type(within(form).getByLabelText(t.phone), '+216 20 000 003');
    await user.click(within(form).getByRole('button', { name: t.create }));

    expect(await screen.findByRole('heading', { name: 'Chiraz Test' })).toBeInTheDocument();
    expect(router.state.location.pathname).toMatch(/^\/patients\/patient-/);
    expect(sent).toEqual({
      fullName: 'Chiraz Test',
      phone: '+216 20 000 003',
      preferredLanguage: 'FR',
    });
    server.events.removeAllListeners();
  });

  it('explains a reference conflict', async () => {
    renderAt('/patients');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: t.add }));
    const form = screen.getByRole('form', { name: t.addTitle });

    await user.type(within(form).getByLabelText(t.fullName), 'Dup Test');
    await user.type(within(form).getByLabelText(t.ref), 'P-0001');
    await user.click(within(form).getByRole('button', { name: t.create }));

    expect(await within(form).findByRole('alert')).toHaveTextContent(t.refConflict);
  });

  it('shows a patient with their appointments', async () => {
    renderAt('/patients/patient-001');

    expect(await screen.findByRole('heading', { name: 'Amel Test' })).toBeInTheDocument();
    expect(
      await screen.findByRole('link', { name: /Consultation · Dr Karim Test/ }),
    ).toHaveAttribute('href', '/appointments/appt-001');
  });

  it('hides creation from practitioners', async () => {
    renderAt('/patients', ['PRACTITIONER']);

    expect(await screen.findByText('Amel Test')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.add })).not.toBeInTheDocument();
  });
});
