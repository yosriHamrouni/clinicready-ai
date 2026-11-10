import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import { get } from '../api/client';

function renderApp(initialEntry: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText('E-mail'), email);
  await user.type(screen.getByLabelText('Mot de passe'), password);
  await user.click(screen.getByRole('button', { name: 'Se connecter' }));
}

describe('auth flow', () => {
  it('redirects anonymous visitors from /dashboard to /login', async () => {
    const router = renderApp('/dashboard');
    expect(await screen.findByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('redirects back to the originally requested page after login', async () => {
    const router = renderApp('/patients');
    await fillAndSubmit('reception@clinic.test', 'password');

    expect(await screen.findByRole('heading', { name: 'Patients' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/patients');
    expect(screen.getByText('Sarra Test')).toBeInTheDocument();
  });

  it('shows an error message on failed login', async () => {
    renderApp('/login');
    await fillAndSubmit('reception@clinic.test', 'wrong');

    expect(await screen.findByRole('alert')).toHaveTextContent('Identifiants invalides');
  });

  it('validates required fields and email format client-side', async () => {
    renderApp('/login');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('E-mail'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByText("Format d'e-mail invalide")).toBeInTheDocument();
    expect(screen.getByText('Le mot de passe est requis')).toBeInTheDocument();
  });

  it('clears the session and lands on /login when the API returns 401', async () => {
    const { http, HttpResponse } = await import('msw');
    const { server } = await import('../mocks/server');

    const router = renderApp('/dashboard');
    await fillAndSubmit('reception@clinic.test', 'password');
    expect(await screen.findByRole('heading', { name: 'Tableau de bord' })).toBeInTheDocument();

    server.use(
      http.get('*/patients', () =>
        HttpResponse.json({ code: 'EXPIRED', message: 'Session expired' }, { status: 401 }),
      ),
    );
    await act(async () => {
      await get('/patients').catch(() => undefined);
    });

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(await screen.findByRole('button', { name: 'Se connecter' })).toBeInTheDocument();
  });
});
