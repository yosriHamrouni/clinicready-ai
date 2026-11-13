import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ApiError } from '../../api/client';
import { appointmentsApi } from '../../api/endpoints/appointments';
import type {
  AdminDetailsInput,
  Appointment,
  ChecklistItemStatus,
  ContactMethod,
} from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { formatDateTime } from '../../lib/dates';
import { strings } from '../../lib/strings';
import { isWellFormedEmail, isWellFormedPhone } from '../../lib/validation';
import { AppointmentStatusBadge, ChecklistItemBadge } from './badges';
import { selectClass } from './styles';

const t = strings.appointments;
const c = strings.checklist;
const appointmentKey = (id: string) => ['appointment', id] as const;

export function AppointmentDetailPage() {
  const { id = '' } = useParams();
  const appointment = useQuery({
    queryKey: appointmentKey(id),
    queryFn: () => appointmentsApi.get(id),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  });

  return (
    <div className="space-y-6">
      <Link
        to="/appointments"
        className="inline-flex items-center gap-1 text-sm text-primary hover:underline"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        {t.back}
      </Link>
      {appointment.isPending ? (
        <LoadingSkeleton />
      ) : appointment.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {appointment.error instanceof ApiError && appointment.error.status === 404
            ? t.notFound
            : t.loadError}
        </p>
      ) : (
        <AppointmentDetail appointment={appointment.data} />
      )}
    </div>
  );
}

function AppointmentDetail({ appointment: a }: { appointment: Appointment }) {
  const { hasRole } = useAuth();
  const canWrite = hasRole('RECEPTIONIST') || hasRole('CLINIC_MANAGER');

  return (
    <>
      <PageHeader
        title={`${a.patient.fullName} · ${a.appointmentType.label}`}
        description={`${formatDateTime(a.startsAt)} · ${a.practitioner.fullName}`}
        actions={<AppointmentStatusBadge status={a.status} />}
      />
      <p className="text-sm">
        <Link to={`/patients/${a.patient.id}`} className="text-primary hover:underline">
          {a.patient.patientRef}
        </Link>
      </p>
      <Card>
        <CardHeader>
          <CardTitle>{c.title}</CardTitle>
          <p className="text-sm text-muted-foreground">{c.description}</p>
        </CardHeader>
        <CardContent className="space-y-6">
          {!canWrite && <p className="text-sm text-muted-foreground">{c.readOnly}</p>}
          {/* Re-mount the form when the server copy changes so the draft starts from it. */}
          <AdminDetailsForm key={a.updatedAt} appointment={a} canWrite={canWrite} />
        </CardContent>
      </Card>
    </>
  );
}

interface Draft {
  contactName: string;
  contactMethod: ContactMethod;
  contactValue: string;
  examinations: string;
  noExaminations: boolean;
  advance: string;
}

function toDraft(a: Appointment): Draft {
  const { trustedContact, expectedExaminations, advance } = a.adminDetails;
  return {
    contactName: trustedContact.name ?? '',
    contactMethod: trustedContact.method ?? 'PHONE',
    contactValue: trustedContact.value ?? '',
    examinations: (expectedExaminations.items ?? []).join('\n'),
    noExaminations: expectedExaminations.items?.length === 0,
    advance: advance.amount == null ? '' : String(advance.amount),
  };
}

interface Checked {
  input: AdminDetailsInput | null;
  contactError?: string;
  advanceError?: string;
}

/** Turns the draft into a request body; empty fields mean "not provided". */
function check(d: Draft): Checked {
  const name = d.contactName.trim();
  const value = d.contactValue.trim();
  let contactError: string | undefined;
  let trustedContact: AdminDetailsInput['trustedContact'] = null;
  if (name || value) {
    if (!name || !value) contactError = c.contactIncomplete;
    else if (d.contactMethod === 'PHONE' ? !isWellFormedPhone(value) : !isWellFormedEmail(value))
      contactError = c.contactInvalid;
    else trustedContact = { name, method: d.contactMethod, value };
  }

  const lines = d.examinations
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);
  const expectedExaminations = d.noExaminations ? [] : lines.length ? lines : null;

  let advanceError: string | undefined;
  let advanceAmount: number | null = null;
  const advance = d.advance.trim().replace(',', '.');
  if (advance) {
    if (!/^\d{1,9}(\.\d{1,3})?$/.test(advance)) advanceError = c.advanceInvalid;
    else advanceAmount = Number(advance);
  }

  const input =
    contactError || advanceError ? null : { trustedContact, expectedExaminations, advanceAmount };
  return { input, contactError, advanceError };
}

function AdminDetailsForm({
  appointment,
  canWrite,
}: {
  appointment: Appointment;
  canWrite: boolean;
}) {
  const id = useId();
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<Draft>(() => toDraft(appointment));
  const [saved, setSaved] = useState(false);
  const { input, contactError, advanceError } = check(draft);
  const dirty = JSON.stringify(draft) !== JSON.stringify(toDraft(appointment));
  const { trustedContact, expectedExaminations, advance } = appointment.adminDetails;

  const onUpdated = (updated: Appointment) => {
    queryClient.setQueryData(appointmentKey(updated.id), updated);
    void queryClient.invalidateQueries({ queryKey: ['appointments'] });
  };
  const save = useMutation({
    mutationFn: (body: AdminDetailsInput) =>
      appointmentsApi.updateAdminDetails(appointment.id, body),
    onSuccess: (updated) => {
      setSaved(true);
      onUpdated(updated);
    },
  });
  const confirm = useMutation({
    mutationFn: (action: () => Promise<Appointment>) => action(),
    onSuccess: onUpdated,
  });

  const set = (patch: Partial<Draft>) => {
    setSaved(false);
    setDraft((d) => ({ ...d, ...patch }));
  };
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (input && dirty) save.mutate(input);
  };
  const disabled = !canWrite;

  return (
    <form aria-label={c.title} onSubmit={onSubmit} className="space-y-6">
      <Section title={c.trustedContact} status={trustedContact.status}>
        <div className="grid gap-4 sm:grid-cols-3">
          <Field id={`${id}-contact-name`} label={c.contactName}>
            <Input
              id={`${id}-contact-name`}
              value={draft.contactName}
              disabled={disabled}
              maxLength={200}
              onChange={(e) => set({ contactName: e.target.value })}
            />
          </Field>
          <Field id={`${id}-contact-method`} label={c.contactMethod}>
            <select
              id={`${id}-contact-method`}
              className={selectClass}
              value={draft.contactMethod}
              disabled={disabled}
              onChange={(e) => set({ contactMethod: e.target.value as ContactMethod })}
            >
              {(['PHONE', 'EMAIL'] as const).map((m) => (
                <option key={m} value={m}>
                  {c.contactMethods[m]}
                </option>
              ))}
            </select>
          </Field>
          <Field id={`${id}-contact-value`} label={c.contactValue}>
            <Input
              id={`${id}-contact-value`}
              value={draft.contactValue}
              disabled={disabled}
              inputMode={draft.contactMethod === 'PHONE' ? 'tel' : 'email'}
              aria-invalid={Boolean(contactError)}
              aria-describedby={`${id}-contact-hint`}
              onChange={(e) => set({ contactValue: e.target.value })}
            />
          </Field>
        </div>
        <p
          id={`${id}-contact-hint`}
          className={contactError ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}
        >
          {contactError ?? c.contactHint}
        </p>
        <ConfirmationRow
          confirmedAt={trustedContact.confirmation?.at}
          canConfirm={canWrite && trustedContact.status !== 'MISSING' && !dirty}
          confirmLabel={c.confirmContact}
          revokeLabel={c.revokeContact}
          pending={confirm.isPending}
          onConfirm={() =>
            confirm.mutate(() => appointmentsApi.confirmTrustedContact(appointment.id))
          }
          onRevoke={() =>
            confirm.mutate(() => appointmentsApi.revokeTrustedContact(appointment.id))
          }
        />
      </Section>

      <Section title={c.examinations} status={expectedExaminations.status}>
        <Field id={`${id}-exams`} label={c.examinations}>
          <textarea
            id={`${id}-exams`}
            rows={3}
            className="flex w-full rounded-md border border-input bg-card px-3 py-2 text-base md:text-sm"
            value={draft.examinations}
            disabled={disabled || draft.noExaminations}
            aria-describedby={`${id}-exams-hint`}
            onChange={(e) => set({ examinations: e.target.value })}
          />
        </Field>
        <p id={`${id}-exams-hint`} className="text-xs text-muted-foreground">
          {c.examinationsHint}
        </p>
        <div className="flex items-center gap-2">
          <input
            id={`${id}-no-exams`}
            type="checkbox"
            className="h-5 w-5 rounded border-input accent-primary"
            checked={draft.noExaminations}
            disabled={disabled}
            onChange={(e) => set({ noExaminations: e.target.checked })}
          />
          <Label htmlFor={`${id}-no-exams`}>{c.noExaminations}</Label>
        </div>
      </Section>

      <Section title={c.advance} status={advance.status}>
        <Field id={`${id}-advance`} label={c.advance}>
          <Input
            id={`${id}-advance`}
            inputMode="decimal"
            className="max-w-xs"
            value={draft.advance}
            disabled={disabled}
            aria-invalid={Boolean(advanceError)}
            aria-describedby={`${id}-advance-hint`}
            onChange={(e) => set({ advance: e.target.value })}
          />
        </Field>
        <p
          id={`${id}-advance-hint`}
          className={advanceError ? 'text-xs text-destructive' : 'text-xs text-muted-foreground'}
        >
          {advanceError ?? `${c.advanceHint} ${c.paymentHint}`}
        </p>
        <ConfirmationRow
          confirmedAt={advance.paymentConfirmation?.at}
          canConfirm={canWrite && advance.status !== 'MISSING' && !dirty}
          confirmLabel={c.confirmPayment}
          revokeLabel={c.revokePayment}
          pending={confirm.isPending}
          onConfirm={() => confirm.mutate(() => appointmentsApi.confirmPayment(appointment.id))}
          onRevoke={() => confirm.mutate(() => appointmentsApi.revokePayment(appointment.id))}
        />
      </Section>

      {canWrite && (
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" disabled={!input || !dirty || save.isPending}>
            {save.isPending ? c.saving : c.save}
          </Button>
          {saved && !dirty && (
            <span role="status" className="text-sm text-muted-foreground">
              {c.saved}
            </span>
          )}
          {(save.error ?? confirm.error) && (
            <span role="alert" className="text-sm text-destructive">
              {c.saveError}
            </span>
          )}
        </div>
      )}
    </form>
  );
}

function Section({
  title,
  status,
  children,
}: {
  title: string;
  status: ChecklistItemStatus;
  children: ReactNode;
}) {
  return (
    <fieldset className="space-y-3 border-b border-border pb-6 last-of-type:border-0">
      <legend className="flex w-full flex-wrap items-center justify-between gap-2 text-base font-semibold">
        {title}
        <ChecklistItemBadge status={status} />
      </legend>
      {children}
    </fieldset>
  );
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  );
}

function ConfirmationRow({
  confirmedAt,
  canConfirm,
  confirmLabel,
  revokeLabel,
  pending,
  onConfirm,
  onRevoke,
}: {
  confirmedAt?: string;
  canConfirm: boolean;
  confirmLabel: string;
  revokeLabel: string;
  pending: boolean;
  onConfirm: () => void;
  onRevoke: () => void;
}) {
  if (confirmedAt) {
    return (
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm text-muted-foreground">
          {c.confirmedOn(formatDateTime(confirmedAt))}
        </span>
        {canConfirm && (
          <Button type="button" variant="outline" disabled={pending} onClick={onRevoke}>
            {revokeLabel}
          </Button>
        )}
      </div>
    );
  }
  return canConfirm ? (
    <Button type="button" variant="outline" disabled={pending} onClick={onConfirm}>
      {confirmLabel}
    </Button>
  ) : null;
}
