import { useId, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { CalendarX, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import { appointmentsApi } from '../../api/endpoints/appointments';
import {
  APPOINTMENT_STATUSES,
  type Appointment,
  type AppointmentQuery,
  type AppointmentStatus,
} from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { EmptyState } from '../../components/EmptyState';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card, CardContent } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import {
  addDays,
  formatDay,
  formatTime,
  parseDateInput,
  startOfWeek,
  toDateInput,
} from '../../lib/dates';
import { strings } from '../../lib/strings';
import { cn } from '../../lib/utils';
import { AppointmentStatusBadge, ChecklistItemBadge } from './badges';
import { NewAppointmentForm } from './NewAppointmentForm';
import { selectClass } from './styles';

const t = strings.appointments;
type View = 'day' | 'week';

export function AppointmentsPage() {
  const { hasRole } = useAuth();
  const canWrite = hasRole('RECEPTIONIST') || hasRole('CLINIC_MANAGER');
  const [params, setParams] = useSearchParams();
  const [adding, setAdding] = useState(false);

  const view: View = params.get('view') === 'week' ? 'week' : 'day';
  const date = parseDateInput(params.get('date'));
  const practitionerId = params.get('practitioner') ?? '';
  const status = (params.get('status') ?? '') as AppointmentStatus | '';

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  const from = view === 'week' ? startOfWeek(date) : date;
  const to = addDays(from, view === 'week' ? 7 : 1);
  const query: AppointmentQuery = {
    from: from.toISOString(),
    to: to.toISOString(),
    practitionerId: practitionerId || undefined,
    status: status || undefined,
  };

  const appointments = useQuery({
    queryKey: ['appointments', query],
    queryFn: () => appointmentsApi.list(query),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title={t.title}
        description={t.description}
        actions={
          canWrite && !adding ? (
            <Button className="w-full sm:w-auto" onClick={() => setAdding(true)}>
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t.add}
            </Button>
          ) : undefined
        }
      />

      {adding && (
        <NewAppointmentForm
          defaultDate={date}
          onDone={() => setAdding(false)}
          onCancel={() => setAdding(false)}
        />
      )}

      <Filters
        view={view}
        date={date}
        practitionerId={practitionerId}
        status={status}
        onChange={setParam}
      />

      {appointments.isPending ? (
        <LoadingSkeleton />
      ) : appointments.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t.loadError}
        </p>
      ) : view === 'week' ? (
        <WeekCalendar start={from} appointments={appointments.data} />
      ) : appointments.data.length === 0 ? (
        <EmptyState icon={CalendarX} title={t.empty} description={formatDay(date)} />
      ) : (
        <AppointmentTable appointments={appointments.data} />
      )}
    </div>
  );
}

function Filters({
  view,
  date,
  practitionerId,
  status,
  onChange,
}: {
  view: View;
  date: Date;
  practitionerId: string;
  status: string;
  onChange: (key: string, value: string) => void;
}) {
  const id = useId();
  const practitioners = useQuery({
    queryKey: ['practitioners'],
    queryFn: appointmentsApi.practitioners,
  });
  const step = view === 'week' ? 7 : 1;

  return (
    <Card>
      <CardContent className="grid gap-4 pt-4 sm:grid-cols-2 md:pt-6 lg:grid-cols-4 lg:items-end">
        <div className="space-y-2">
          <Label htmlFor={`${id}-date`}>{t.date}</Label>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="icon"
              aria-label={t.previous}
              onClick={() => onChange('date', toDateInput(addDays(date, -step)))}
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Input
              id={`${id}-date`}
              type="date"
              value={toDateInput(date)}
              onChange={(e) => onChange('date', e.target.value)}
            />
            <Button
              variant="outline"
              size="icon"
              aria-label={t.next}
              onClick={() => onChange('date', toDateInput(addDays(date, step)))}
            >
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${id}-practitioner`}>{t.practitioner}</Label>
          <select
            id={`${id}-practitioner`}
            className={selectClass}
            value={practitionerId}
            onChange={(e) => onChange('practitioner', e.target.value)}
          >
            <option value="">{t.allPractitioners}</option>
            {practitioners.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.fullName}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <Label htmlFor={`${id}-status`}>{t.status}</Label>
          <select
            id={`${id}-status`}
            className={selectClass}
            value={status}
            onChange={(e) => onChange('status', e.target.value)}
          >
            <option value="">{t.allStatuses}</option>
            {APPOINTMENT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {t.statuses[s]}
              </option>
            ))}
          </select>
        </div>
        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">{t.view}</legend>
          <div className="flex gap-2" role="group">
            {(['day', 'week'] as const).map((v) => (
              <Button
                key={v}
                type="button"
                variant={view === v ? 'default' : 'outline'}
                aria-pressed={view === v}
                className="flex-1"
                onClick={() => onChange('view', v === 'day' ? '' : v)}
              >
                {v === 'day' ? t.dayView : t.weekView}
              </Button>
            ))}
          </div>
        </fieldset>
      </CardContent>
    </Card>
  );
}

function AppointmentTable({ appointments }: { appointments: Appointment[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-border bg-card">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-border text-xs text-muted-foreground">
          <tr>
            <th className="px-4 py-3 font-medium">{t.time}</th>
            <th className="px-4 py-3 font-medium">{t.patient}</th>
            <th className="px-4 py-3 font-medium">{t.practitioner}</th>
            <th className="px-4 py-3 font-medium">{t.type}</th>
            <th className="px-4 py-3 font-medium">{t.status}</th>
            <th className="px-4 py-3 font-medium">{t.checklist}</th>
          </tr>
        </thead>
        <tbody>
          {appointments.map((a) => (
            <tr key={a.id} className="border-b border-border last:border-0">
              <td className="whitespace-nowrap px-4 py-3">
                <Link
                  className="font-semibold text-primary hover:underline"
                  to={`/appointments/${a.id}`}
                >
                  {formatTime(a.startsAt)}
                </Link>
              </td>
              <td className="px-4 py-3">
                <span className="block">{a.patient.fullName}</span>
                <span className="text-xs text-muted-foreground">{a.patient.patientRef}</span>
              </td>
              <td className="px-4 py-3">{a.practitioner.fullName}</td>
              <td className="px-4 py-3">{a.appointmentType.label}</td>
              <td className="px-4 py-3">
                <AppointmentStatusBadge status={a.status} />
              </td>
              <td className="px-4 py-3">
                <ChecklistSummary appointment={a} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Worst status among the checklist items: one missing item makes the whole file "missing". */
function ChecklistSummary({ appointment }: { appointment: Appointment }) {
  const d = appointment.adminDetails;
  const statuses = [d.trustedContact.status, d.expectedExaminations.status, d.advance.status];
  const worst = statuses.includes('MISSING')
    ? 'MISSING'
    : statuses.includes('PROVIDED')
      ? 'PROVIDED'
      : 'CONFIRMED';
  return <ChecklistItemBadge status={worst} />;
}

function WeekCalendar({ start, appointments }: { start: Date; appointments: Appointment[] }) {
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(start, i)), [start]);
  const byDay = useMemo(() => {
    const map = new Map<string, Appointment[]>();
    for (const a of appointments) {
      const key = toDateInput(new Date(a.startsAt));
      map.set(key, [...(map.get(key) ?? []), a]);
    }
    return map;
  }, [appointments]);
  const today = toDateInput(new Date());

  return (
    <div className="grid gap-3 md:grid-cols-7">
      {days.map((day) => {
        const key = toDateInput(day);
        const items = byDay.get(key) ?? [];
        return (
          <section
            key={key}
            aria-label={formatDay(day)}
            className={cn(
              'min-h-24 rounded-lg border border-border bg-card p-2',
              key === today && 'border-primary',
            )}
          >
            <h2 className="mb-2 text-xs font-semibold capitalize text-muted-foreground">
              {formatDay(day)}
            </h2>
            {items.length === 0 ? (
              <p className="text-xs text-muted-foreground">—</p>
            ) : (
              <ul className="space-y-2">
                {items.map((a) => (
                  <li key={a.id}>
                    <Link
                      to={`/appointments/${a.id}`}
                      className={cn(
                        'block rounded-md border border-border p-2 text-xs hover:bg-accent',
                        a.status === 'CANCELLED' && 'line-through opacity-70',
                      )}
                    >
                      <span className="font-semibold">{formatTime(a.startsAt)}</span>{' '}
                      {a.patient.fullName}
                      <span className="block text-muted-foreground">
                        {a.appointmentType.label} · {a.practitioner.fullName}
                      </span>
                      <span className="sr-only">{t.statuses[a.status]}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}
