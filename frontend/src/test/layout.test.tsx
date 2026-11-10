import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import { READINESS_STATUSES, StatusBadge } from '../components/StatusBadge';
import { strings } from '../lib/strings';

/** Renders the app with an already-restored mock session. */
function renderLoggedIn(initialEntry: string) {
  sessionStorage.setItem('mock-session', '1');
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
  return router;
}

describe('app shell', () => {
  it('opens the drawer from the hamburger on mobile and closes it after navigating', async () => {
    window.innerWidth = 375;
    const router = renderLoggedIn('/dashboard');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: strings.a11y.openMenu }));
    const drawer = await screen.findByRole('dialog');
    await user.click(within(drawer).getByRole('link', { name: strings.nav.patients }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe('/patients');
  });

  it('closes the drawer with Escape', async () => {
    window.innerWidth = 375;
    renderLoggedIn('/dashboard');
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: strings.a11y.openMenu }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('marks the active nav item with aria-current="page"', async () => {
    renderLoggedIn('/patients');
    const [nav] = await screen.findAllByRole('navigation', { name: strings.a11y.mainNav });

    expect(within(nav!).getByRole('link', { name: strings.nav.patients })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(within(nav!).getByRole('link', { name: strings.nav.dashboard })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('puts the skip link first in the tab order', async () => {
    renderLoggedIn('/dashboard');
    await screen.findByRole('heading', { name: strings.dashboard.title });
    const user = userEvent.setup();
    await user.tab();

    expect(screen.getByRole('link', { name: strings.a11y.skipToContent })).toHaveFocus();
  });
});

describe('StatusBadge', () => {
  it.each(READINESS_STATUSES)('renders a text label for %s', (status) => {
    render(<StatusBadge status={status} />);
    expect(screen.getByText(strings.status[status])).toBeInTheDocument();
  });
});
