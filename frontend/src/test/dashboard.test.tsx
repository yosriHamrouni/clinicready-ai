import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.dashboard;

function renderAt(path: string) {
  server.use(http.get('*/auth/me', () => HttpResponse.json(MOCK_USER)));
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

beforeEach(() => resetMockClinicData());

const kpi = (label: string) => screen.getByRole('group', { name: label });

describe('readiness dashboard', () => {
  it('counts upcoming appointments by readiness and pending follow-ups', async () => {
    renderAt('/dashboard');

    const row = (await screen.findByText('Amel Test')).closest('tr')!;
    expect(within(row).getByText(strings.aggregate.INCOMPLETE)).toBeInTheDocument();
    expect(within(row).getByText(/No INSURANCE_CARD document/)).toBeInTheDocument();
    // The cancelled appointment is not upcoming.
    expect(screen.queryByText('Bechir Test')).not.toBeInTheDocument();

    expect(within(kpi(t.kpis.upcoming)).getByText('1')).toBeInTheDocument();
    expect(within(kpi(t.kpis.incomplete)).getByText('1')).toBeInTheDocument();
    expect(within(kpi(t.kpis.ready)).getByText('0')).toBeInTheDocument();
    expect(await within(kpi(t.kpis.pendingFollowUps)).findByText('1')).toBeInTheDocument();

    expect(screen.getByRole('link', { name: t.cta })).toHaveAttribute('href', '/agent?prepare=1');
  });

  it('filters by readiness status and practitioner', async () => {
    renderAt('/dashboard');
    const user = userEvent.setup();
    await screen.findByText('Amel Test');

    await user.selectOptions(screen.getByLabelText(t.readiness), strings.aggregate.READY);
    expect(await screen.findByText(t.empty)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText(t.readiness), strings.aggregate.INCOMPLETE);
    expect(await screen.findByText('Amel Test')).toBeInTheDocument();

    await user.selectOptions(
      screen.getByLabelText(strings.appointments.practitioner),
      'Dr Leila Test',
    );
    expect(await screen.findByText(t.empty)).toBeInTheDocument();
  });

  it('opens the checklist with reasons and evidence links', async () => {
    renderAt('/dashboard');
    const user = userEvent.setup();
    const row = (await screen.findByText('Amel Test')).closest('tr')!;
    await user.click(within(row).getByRole('link'));

    expect(await screen.findByText(strings.readiness.title)).toBeInTheDocument();
    expect(await screen.findByText('ID_DOCUMENT text extraction failed')).toBeInTheDocument();
    expect(screen.getByText(/Ask the patient for an INSURANCE_CARD/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /v1/ })).toHaveAttribute(
      'href',
      '/patients/patient-001?document=doc-002',
    );
  });
});
