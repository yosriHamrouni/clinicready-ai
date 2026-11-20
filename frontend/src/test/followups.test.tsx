import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.followUps;

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

describe('follow-up center', () => {
  it('lists open follow-ups and opens one with its draft', async () => {
    renderAt('/follow-ups');
    const user = userEvent.setup();

    const list = await screen.findByRole('region', { name: t.list });
    expect(within(list).getByText(strings.documentCategories.INSURANCE_CARD)).toBeInTheDocument();
    expect(within(list).getByText(t.statuses.AWAITING_APPROVAL)).toBeInTheDocument();

    await user.click(within(list).getByRole('button', { name: /Carte d'assurance/ }));
    expect(await screen.findByLabelText(t.draftLabel)).toHaveValue(
      "Bonjour, il manque votre carte d'assurance pour votre rendez-vous. Merci de nous l'envoyer.",
    );
    expect(screen.getByRole('link', { name: t.openAppointment })).toHaveAttribute(
      'href',
      '/appointments/appt-001',
    );
  });

  it('opens the task from the link used by the agent workspace', async () => {
    renderAt('/follow-ups?task=task-001');
    expect(await screen.findByLabelText(t.draftLabel)).toBeInTheDocument();
  });

  it('edits the draft, approves, then marks it sent and resolved', async () => {
    renderAt('/follow-ups?task=task-001');
    const user = userEvent.setup();

    const draft = await screen.findByLabelText(t.draftLabel);
    await user.clear(draft);
    await user.type(draft, 'Message corrigé');
    await user.click(screen.getByRole('button', { name: t.saveDraft }));
    expect(await screen.findByText(t.draftSaved)).toBeInTheDocument();
    expect(await screen.findByText(t.draftEdited)).toBeInTheDocument();

    await user.type(screen.getByLabelText(t.commentLabel), 'ok pour envoi');
    await user.click(screen.getByRole('button', { name: t.approve }));
    expect(await screen.findByText(t.noAutoSend)).toBeInTheDocument();
    expect(screen.getByText('ok pour envoi')).toBeInTheDocument();
    // The draft is locked once decided.
    expect(screen.getByLabelText(t.draftLabel)).toBeDisabled();

    await user.click(screen.getByRole('button', { name: t.markDelivered }));
    await user.click(await screen.findByRole('button', { name: t.resolve }));
    await user.click(screen.getByRole('button', { name: t.filters.RESOLVED }));
    const list = await screen.findByRole('region', { name: t.list });
    expect(await within(list).findByText(t.statuses.RESOLVED)).toBeInTheDocument();
  });

  it('only rejects with a comment', async () => {
    renderAt('/follow-ups?task=task-001');
    const user = userEvent.setup();

    const reject = await screen.findByRole('button', { name: t.reject });
    expect(reject).toBeDisabled();
    expect(screen.getByText(t.commentRequired)).toBeInTheDocument();

    await user.type(screen.getByLabelText(t.commentLabel), 'Patient déjà relancé');
    await user.click(reject);
    // The state badge and the decision entry both say "Rejetée".
    expect(await screen.findByText('Patient déjà relancé')).toBeInTheDocument();
    expect(screen.getAllByText(t.decisions.REJECTED)).toHaveLength(2);
    expect(screen.queryByRole('button', { name: t.approve })).not.toBeInTheDocument();
  });

  it('is read-only for practitioners', async () => {
    renderAt('/follow-ups?task=task-001', { ...MOCK_USER, roles: ['PRACTITIONER'] });

    expect(await screen.findByText(t.readOnly)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.approve })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.saveDraft })).not.toBeInTheDocument();
    expect(screen.getByLabelText(t.draftLabel)).toBeDisabled();
  });

  it('filters by status and shows the empty state', async () => {
    renderAt('/follow-ups');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: t.filters.DELIVERED }));
    expect(await screen.findByText(t.emptyTitle)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.filters.DELIVERED })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('tells the user when an action fails', async () => {
    server.use(http.post('*/follow-up-tasks/:id/approve', () => HttpResponse.json(
      { code: 'INVALID_TRANSITION', message: 'no' },
      { status: 409 },
    )));
    renderAt('/follow-ups?task=task-001');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: t.approve }));
    expect(await screen.findByRole('alert')).toHaveTextContent(t.actionError);
  });
});
