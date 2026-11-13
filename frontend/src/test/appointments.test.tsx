import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import type { AdminDetailsInput } from '../api/types';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.appointments;
const c = strings.checklist;

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

describe('appointment list and calendar', () => {
  it("lists today's appointments and filters them by practitioner and status", async () => {
    const requested: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.includes('/appointments?')) requested.push(request.url);
    });
    renderAt('/appointments');
    const user = userEvent.setup();

    expect(await screen.findByText('Amel Test')).toBeInTheDocument();
    expect(screen.getByText('Bechir Test')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText(t.statuses.CANCELLED)).toBeInTheDocument();

    await user.selectOptions(await screen.findByLabelText(t.practitioner), 'Dr Karim Test');
    await waitFor(() => expect(screen.queryByText('Bechir Test')).not.toBeInTheDocument());
    expect(screen.getByText('Amel Test')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(t.status), t.statuses.CANCELLED);
    expect(await screen.findByText(t.empty)).toBeInTheDocument();

    const last = new URL(requested[requested.length - 1]!);
    expect(last.searchParams.get('practitionerId')).toBe('prac-001');
    expect(last.searchParams.get('status')).toBe('CANCELLED');
    expect(last.searchParams.get('from')).toBeTruthy();
    server.events.removeAllListeners();
  });

  it('shows a week calendar with each appointment on its day', async () => {
    renderAt('/appointments?view=week');

    const link = await screen.findByRole('link', { name: /Amel Test/ });
    expect(link).toHaveAttribute('href', '/appointments/appt-001');
    const day = link.closest('section')!;
    expect(within(day).getByRole('link', { name: /Bechir Test/ })).toBeInTheDocument();
    expect(screen.getAllByRole('region')).toHaveLength(7);
  });

  it('creates an appointment', async () => {
    renderAt('/appointments');
    const user = userEvent.setup();
    await screen.findByText('Amel Test', undefined, { timeout: 5000 });

    await user.click(screen.getByRole('button', { name: t.add }));
    const form = screen.getByRole('form', { name: t.addTitle });
    await user.selectOptions(within(form).getByLabelText(t.patient), 'Bechir Test (P-0002)');
    await user.selectOptions(within(form).getByLabelText(t.practitioner), 'Dr Karim Test');
    await user.selectOptions(within(form).getByLabelText(t.type), 'IRM');
    await user.click(within(form).getByRole('button', { name: t.create }));

    await waitFor(() =>
      expect(screen.queryByRole('form', { name: t.addTitle })).not.toBeInTheDocument(),
    );
    await waitFor(() => expect(screen.getAllByText('Bechir Test')).toHaveLength(2));
  });
});

describe('appointment administrative checklist', () => {
  it('keeps missing, provided and confirmed apart', async () => {
    let sent: AdminDetailsInput | undefined;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PUT') sent = (await request.clone().json()) as AdminDetailsInput;
    });
    renderAt('/appointments/appt-001');
    const user = userEvent.setup();

    const form = await screen.findByRole('form', { name: c.title });
    expect(within(form).getAllByText(c.itemStatus.MISSING)).toHaveLength(3);
    expect(within(form).queryByRole('button', { name: c.confirmPayment })).not.toBeInTheDocument();

    await user.type(within(form).getByLabelText(c.contactName), 'Sami Test');
    await user.type(within(form).getByLabelText(c.contactValue), '+216 20 000 000');
    await user.click(within(form).getByLabelText(c.noExaminations));
    await user.type(within(form).getByLabelText(c.advance), '150,5');
    await user.click(within(form).getByRole('button', { name: c.save }));

    // The form re-mounts from the saved copy, so query it again.
    expect(await screen.findByRole('button', { name: c.confirmPayment })).toBeInTheDocument();
    expect(sent).toEqual({
      trustedContact: { name: 'Sami Test', method: 'PHONE', value: '+216 20 000 000' },
      expectedExaminations: [],
      advanceAmount: 150.5,
    });
    // Provided is not confirmed: a well-formed number does not prove the contact is reachable.
    const updated = await screen.findByRole('form', { name: c.title });
    expect(within(updated).getAllByText(c.itemStatus.PROVIDED)).toHaveLength(3);

    await user.click(within(updated).getByRole('button', { name: c.confirmPayment }));
    expect(await screen.findByText(c.itemStatus.CONFIRMED)).toBeInTheDocument();
    expect(screen.getAllByText(c.itemStatus.PROVIDED)).toHaveLength(2);
    server.events.removeAllListeners();
  });

  it('blocks an invalid contact or amount before calling the API', async () => {
    renderAt('/appointments/appt-001');
    const user = userEvent.setup();
    const form = await screen.findByRole('form', { name: c.title });

    await user.type(within(form).getByLabelText(c.contactName), 'Sami Test');
    expect(within(form).getByText(c.contactIncomplete)).toBeInTheDocument();
    await user.type(within(form).getByLabelText(c.contactValue), '12');
    expect(within(form).getByText(c.contactInvalid)).toBeInTheDocument();
    await user.clear(within(form).getByLabelText(c.contactValue));
    await user.clear(within(form).getByLabelText(c.contactName));
    await user.type(within(form).getByLabelText(c.advance), '-3');
    expect(within(form).getByText(c.advanceInvalid)).toBeInTheDocument();

    expect(within(form).getByRole('button', { name: c.save })).toBeDisabled();
  });

  it('is read-only for practitioners', async () => {
    renderAt('/appointments/appt-001', ['PRACTITIONER']);

    const form = await screen.findByRole('form', { name: c.title });
    expect(screen.getByText(c.readOnly)).toBeInTheDocument();
    expect(within(form).getByLabelText(c.contactName)).toBeDisabled();
    expect(within(form).queryByRole('button', { name: c.save })).not.toBeInTheDocument();
  });

  it('reports an unknown appointment', async () => {
    renderAt('/appointments/nope');

    expect(await screen.findByRole('alert')).toHaveTextContent(t.notFound);
  });
});
