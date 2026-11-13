import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import type { RequirementRuleInput } from '../api/types';
import { MOCK_USER, resetMockRules } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.requirements;

function renderAs(roles: (typeof MOCK_USER)['roles']) {
  server.use(http.get('*/auth/me', () => HttpResponse.json({ ...MOCK_USER, roles })));
  const router = createMemoryRouter(routes, { initialEntries: ['/settings/requirements'] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
}

async function selectType(label: string) {
  const user = userEvent.setup();
  await user.selectOptions(await screen.findByLabelText(t.appointmentType), label);
  return user;
}

beforeEach(() => resetMockRules());

describe('requirement rules settings', () => {
  it('shows the rules of the selected appointment type', async () => {
    renderAs(['CLINIC_MANAGER']);

    expect(await screen.findByText(t.noRules)).toBeInTheDocument();
    await selectType('IRM');

    const form = await screen.findByRole('form', {
      name: t.ruleFor(strings.documentCategories.CONSENT_FORM),
    });
    expect(within(form).getByLabelText(t.validityDays)).toHaveValue(30);
    expect(within(form).getByLabelText(t.mandatory)).toBeChecked();
    expect(within(form).getByLabelText(t.needsVerification)).toBeChecked();
  });

  it('saves an edited rule with PUT', async () => {
    let sent: RequirementRuleInput | undefined;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PUT') sent = (await request.clone().json()) as RequirementRuleInput;
    });
    renderAs(['CLINIC_MANAGER']);
    const user = await selectType('IRM');
    const form = await screen.findByRole('form', {
      name: t.ruleFor(strings.documentCategories.CONSENT_FORM),
    });
    const save = within(form).getByRole('button', { name: t.save });
    expect(save).toBeDisabled();

    await user.clear(within(form).getByLabelText(t.validityDays));
    await user.click(within(form).getByLabelText(t.mandatory));
    await user.click(save);

    expect(await within(form).findByText(t.saved)).toBeInTheDocument();
    expect(sent).toEqual({
      documentCategory: 'CONSENT_FORM',
      mandatory: false,
      validityDays: null,
      needsVerification: true,
    });
    server.events.removeAllListeners();
  });

  it('blocks an out-of-range validity before calling the API', async () => {
    renderAs(['CLINIC_MANAGER']);
    const user = await selectType('IRM');
    const form = await screen.findByRole('form', {
      name: t.ruleFor(strings.documentCategories.CONSENT_FORM),
    });

    await user.clear(within(form).getByLabelText(t.validityDays));
    await user.type(within(form).getByLabelText(t.validityDays), '0');

    expect(within(form).getByText(t.validityInvalid)).toBeInTheDocument();
    expect(within(form).getByRole('button', { name: t.save })).toBeDisabled();
  });

  it('adds a rule and removes it', async () => {
    renderAs(['CLINIC_MANAGER']);
    await screen.findByText(t.noRules);
    const user = userEvent.setup();
    const add = screen.getByRole('form', { name: t.addTitle });

    await user.selectOptions(within(add).getByLabelText(t.category), 'LAB_RESULT');
    await user.type(within(add).getByLabelText(t.validityDays), '90');
    await user.click(within(add).getByRole('button', { name: t.add }));

    const form = await screen.findByRole('form', {
      name: t.ruleFor(strings.documentCategories.LAB_RESULT),
    });
    expect(within(form).getByLabelText(t.validityDays)).toHaveValue(90);
    // A category that already has a rule is not offered again.
    expect(
      within(screen.getByRole('form', { name: t.addTitle })).queryByRole('option', {
        name: strings.documentCategories.LAB_RESULT,
      }),
    ).not.toBeInTheDocument();

    await user.click(within(form).getByRole('button', { name: t.remove }));
    expect(await screen.findByText(t.noRules)).toBeInTheDocument();
  });

  it('explains a category conflict from the server', async () => {
    server.use(
      http.put('*/requirements/:id', () =>
        HttpResponse.json({ code: 'RULE_CONFLICT', message: 'conflict' }, { status: 409 }),
      ),
    );
    renderAs(['CLINIC_MANAGER']);
    const user = await selectType('IRM');
    const form = await screen.findByRole('form', {
      name: t.ruleFor(strings.documentCategories.CONSENT_FORM),
    });

    await user.click(within(form).getByLabelText(t.needsVerification));
    await user.click(within(form).getByRole('button', { name: t.save }));

    expect(await within(form).findByRole('alert')).toHaveTextContent(t.conflict);
  });

  it('is not available to other roles and hidden from their navigation', async () => {
    renderAs(['RECEPTIONIST']);

    expect(await screen.findByText(t.forbiddenTitle)).toBeInTheDocument();
    expect(screen.queryByLabelText(t.appointmentType)).not.toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.queryByRole('link', { name: strings.nav.requirements }),
      ).not.toBeInTheDocument(),
    );
  });
});
