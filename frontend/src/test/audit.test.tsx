import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.audit;

function renderAt(path: string, user = MOCK_USER) {
  server.use(http.get('*/auth/me', () => HttpResponse.json(user)));
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

beforeEach(() => resetMockClinicData());

describe('audit journal', () => {
  it('lists events with French labels, actor and system author', async () => {
    renderAt('/audit');

    expect(await screen.findByRole('heading', { name: t.title })).toBeInTheDocument();
    const rows = await screen.findAllByRole('row');
    // header + 3 events, newest first
    expect(rows).toHaveLength(4);
    expect(within(rows[1]).getByText(t.actions.AGENT_RUN_FINISHED)).toBeInTheDocument();
    expect(within(rows[1]).getByText('Sarra Test')).toBeInTheDocument();
    expect(within(rows[3]).getByText(t.actions.PATIENT_CREATED)).toBeInTheDocument();
    expect(within(rows[3]).getByText(t.system)).toBeInTheDocument();
    expect(screen.getByText(t.pageOf(1, 1, 3))).toBeInTheDocument();
  });

  it('shows only one run when opened with a run id, and offers the whole journal', async () => {
    renderAt('/audit?runId=run-1');

    expect(await screen.findByRole('heading', { name: t.forRun })).toBeInTheDocument();
    expect(await screen.findAllByRole('row')).toHaveLength(3);
    expect(screen.queryByText(t.actions.PATIENT_CREATED)).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('link', { name: t.showAll }));
    expect(await screen.findAllByRole('row')).toHaveLength(4);
  });

  it('shows an empty state when nothing matches', async () => {
    server.use(
      http.get('*/audit-events', () =>
        HttpResponse.json({ items: [], page: 0, size: 25, total: 0 }),
      ),
    );
    renderAt('/audit');
    expect(await screen.findByText(t.emptyTitle)).toBeInTheDocument();
  });

  it('links a finished agent run to its audit trail', async () => {
    renderAt('/agent');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: strings.agent.examplePrompts[1] }));
    await user.click(screen.getByRole('button', { name: strings.agent.start }));

    expect(await screen.findByRole('link', { name: strings.agent.auditTrail })).toHaveAttribute(
      'href',
      '/audit?runId=run-1',
    );
  });

  it('hides the journal from practitioners in the navigation', async () => {
    renderAt('/dashboard', { ...MOCK_USER, roles: ['PRACTITIONER'] });
    await screen.findAllByRole('navigation');
    expect(screen.queryByRole('link', { name: strings.nav.audit })).not.toBeInTheDocument();
  });
});
