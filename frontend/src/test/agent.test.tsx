import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.agent;

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

describe('agent workspace', () => {
  it('starts a run from an example prompt and shows its timeline and report', async () => {
    renderAt('/agent');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: t.examplePrompts[1] }));
    expect(screen.getByLabelText(t.request)).toHaveValue(t.examplePrompts[1]);
    await user.click(screen.getByRole('button', { name: t.start }));

    expect(await screen.findByText(t.statuses.SUCCEEDED)).toBeInTheDocument();
    const timeline = screen.getByRole('region', { name: t.timeline });
    expect(within(timeline).getByText('getReadiness')).toBeInTheDocument();
    expect(within(timeline).getByText('18 ms')).toBeInTheDocument();

    expect(screen.getByText(t.report)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: t.openAppointment })).toHaveAttribute(
      'href',
      '/appointments/appt-001',
    );
    expect(await screen.findByRole('link', { name: t.openFollowUp })).toHaveAttribute(
      'href',
      '/follow-ups?task=task-001',
    );
  });

  it('starts the prepare run when opened from the dashboard button', async () => {
    const started: string[] = [];
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'POST' && request.url.endsWith('/agent-runs')) {
        started.push(((await request.clone().json()) as { request: string }).request);
      }
    });
    renderAt('/agent?prepare=1');

    expect(await screen.findByText(t.statuses.SUCCEEDED)).toBeInTheDocument();
    expect(started).toEqual([t.prepareRequest]);
    server.events.removeAllListeners();
  });

  it('shows a stopped run plainly without a report', async () => {
    server.use(
      http.get('*/agent-runs/:id', () =>
        HttpResponse.json({
          id: 'run-x',
          request: 'Prépare demain',
          status: 'REPORT_MISMATCH',
          statusReason: 'Report says READY for appt-001 but readiness is INCOMPLETE',
          report: null,
          toolCalls: 1,
          promptTokens: 10,
          completionTokens: 5,
          createdAt: '2026-11-20T10:00:00Z',
          startedAt: '2026-11-20T10:00:00Z',
          finishedAt: '2026-11-20T10:00:01Z',
          actions: [
            {
              seq: 1,
              tool: 'getReadiness',
              arguments: {},
              resultStatus: 'NOT_FOUND',
              latencyMs: 5,
              tokens: 0,
              at: '2026-11-20T10:00:00Z',
            },
          ],
        }),
      ),
    );
    renderAt('/agent?run=run-x');

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText(t.statuses.REPORT_MISMATCH)).toBeInTheDocument();
    expect(within(alert).getByText(/readiness is INCOMPLETE/)).toBeInTheDocument();
    expect(screen.getByText('NOT_FOUND')).toBeInTheDocument();
    expect(screen.queryByText(t.report)).not.toBeInTheDocument();
  });

  it('blocks a request over 500 characters', async () => {
    renderAt('/agent');
    const user = userEvent.setup();
    const box = await screen.findByLabelText(t.request);
    await user.click(box);
    await user.paste('x'.repeat(501));
    expect(screen.getByText(t.tooLong)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.start })).toBeDisabled();
  });
});
