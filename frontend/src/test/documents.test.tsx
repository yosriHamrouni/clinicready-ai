import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { RouterProvider, createMemoryRouter } from 'react-router-dom';
import { Providers } from '../app/providers';
import { routes } from '../app/router';
import type { MetadataPatch } from '../api/types';
import { MOCK_USER, resetMockClinicData } from '../mocks/handlers';
import { server } from '../mocks/server';
import { strings } from '../lib/strings';

const t = strings.documents;

function renderAt(path: string) {
  server.use(http.get('*/auth/me', () => HttpResponse.json(MOCK_USER)));
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  );
}

beforeEach(() => resetMockClinicData());

describe('patient file: documents', () => {
  it('shows the patient, their appointments, and documents with their statuses', async () => {
    renderAt('/patients/patient-001');

    expect(await screen.findByRole('heading', { name: 'Amel Test' })).toBeInTheDocument();
    expect(await screen.findByText('Consultation', { exact: false })).toBeInTheDocument();

    const list = await screen.findByRole('list', { name: t.list });
    expect(within(list).getByText('ordonnance.pdf')).toBeInTheDocument();
    expect(within(list).getByText('cin.png')).toBeInTheDocument();
    expect(within(list).getByText(t.statuses.NEEDS_REVIEW)).toBeInTheDocument();
    expect(within(list).getByText(t.extraction.EXTRACTED)).toBeInTheDocument();
    expect(within(list).getByText(t.extraction.EXTRACTION_FAILED)).toBeInTheDocument();
  });

  it('previews a PDF in a frame and an image as an image', async () => {
    renderAt('/patients/patient-001');
    const user = userEvent.setup();

    const frame = await screen.findByTitle(`${t.preview} : ordonnance.pdf`);
    expect(frame).toHaveAttribute('src', expect.stringContaining('/documents/doc-001/content'));

    await user.click(screen.getByRole('button', { name: /cin\.png/ }));
    const image = await screen.findByRole('img', { name: `${t.preview} : cin.png` });
    expect(image).toHaveAttribute('src', expect.stringContaining('/documents/doc-002/content'));
  });

  it('says so when the patient has no documents', async () => {
    renderAt('/patients/patient-002');
    expect(await screen.findByText(t.empty)).toBeInTheDocument();
  });

  it('saves only the corrected fields and shows who edited and when', async () => {
    let sent: MetadataPatch | undefined;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PATCH' && request.url.endsWith('/documents/doc-001/metadata')) {
        sent = (await request.clone().json()) as MetadataPatch;
      }
    });
    renderAt('/patients/patient-001');
    const user = userEvent.setup();

    expect(await screen.findByText(t.neverEdited)).toBeInTheDocument();
    const save = screen.getByRole('button', { name: t.save });
    expect(save).toBeDisabled();

    await user.type(screen.getByLabelText(t.fields.issuer), 'CNAM');
    await user.type(screen.getByLabelText(t.fields.expiryDate), '2027-03-01');
    await user.click(save);

    expect(await screen.findByRole('status')).toHaveTextContent(t.saved);
    expect(sent).toEqual({ fields: { expiryDate: '2027-03-01', issuer: 'CNAM' } });
    expect(
      screen.getByText(new RegExp(`Dernière modification par ${MOCK_USER.fullName}`)),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(t.fields.issuer)).toHaveValue('CNAM');
    expect(screen.getByRole('button', { name: t.save })).toBeDisabled();
  });

  it('clears a field by emptying it', async () => {
    let last: MetadataPatch | undefined;
    server.events.on('request:start', async ({ request }) => {
      if (request.method === 'PATCH') last = (await request.clone().json()) as MetadataPatch;
    });
    renderAt('/patients/patient-001');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(t.fields.issuer), 'CNAM');
    await user.click(screen.getByRole('button', { name: t.save }));
    await screen.findByRole('status');

    await user.clear(screen.getByLabelText(t.fields.issuer));
    await user.click(screen.getByRole('button', { name: t.save }));

    await waitFor(() => expect(last).toEqual({ fields: { issuer: null } }));
  });

  it('shows an error and keeps the draft when saving fails', async () => {
    server.use(
      http.patch('*/documents/:id/metadata', () =>
        HttpResponse.json({ code: 'VALIDATION_ERROR', message: 'bad' }, { status: 400 }),
      ),
    );
    renderAt('/patients/patient-001');
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText(t.fields.documentNumber), 'A-123');
    await user.click(screen.getByRole('button', { name: t.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(t.saveError);
    expect(screen.getByLabelText(t.fields.documentNumber)).toHaveValue('A-123');
    expect(screen.getByRole('button', { name: t.save })).toBeEnabled();
  });
});
