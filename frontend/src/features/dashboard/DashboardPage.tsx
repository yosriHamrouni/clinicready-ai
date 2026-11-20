import { useId, type ReactNode } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import {
  CalendarClock,
  CalendarX,
  CircleAlert,
  CircleCheck,
  ClipboardList,
  Eye,
  type LucideIcon,
} from 'lucide-react';
import { appointmentsApi } from '../../api/endpoints/appointments';
import { followUpsApi } from '../../api/endpoints/followUps';
import { readinessApi, readinessKey } from '../../api/endpoints/readiness';
import { PENDING_FOLLOW_UP_STATUSES, type AggregateStatus } from '../../api/types';
import { AggregateBadge } from '../../components/AggregateBadge';
import { EmptyState } from '../../components/EmptyState';
import { LoadingSkeleton } from '../../components/LoadingSkeleton';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { addDays, formatDateTime, parseDateInput, toDateInput } from '../../lib/dates';
import { strings } from '../../lib/strings';
import { selectClass } from '../appointments/styles';

const t = strings.dashboard;
const RANGES = [1, 7, 14, 30];
type ReadinessFilter = AggregateStatus | 'NOT_EVALUATED';
const READINESS_FILTERS: ReadinessFilter[] = [
  'READY',
  'INCOMPLETE',
  'NEEDS_REVIEW',
  'NOT_EVALUATED',
];

export function DashboardPage() {
  const id = useId();
  const [params, setParams] = useSearchParams();
  const from = parseDateInput(params.get('from'));
  const days = RANGES.includes(Number(params.get('days'))) ? Number(params.get('days')) : 7;
  const practitionerId = params.get('practitioner') ?? '';
  const readinessFilter = (params.get('readiness') ?? '') as ReadinessFilter | '';

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  const query = {
    from: from.toISOString(),
    to: addDays(from, days).toISOString(),
    practitionerId: practitionerId || undefined,
    status: 'SCHEDULED' as const,
  };
  const appointments = useQuery({
    queryKey: ['appointments', query],
    queryFn: () => appointmentsApi.list(query),
  });
  const practitioners = useQuery({
    queryKey: ['practitioners'],
    queryFn: appointmentsApi.practitioners,
  });
  const followUps = useQuery({
    queryKey: ['follow-ups', PENDING_FOLLOW_UP_STATUSES],
    queryFn: () => followUpsApi.list(PENDING_FOLLOW_UP_STATUSES),
  });
  // shortcut: one readiness request per appointment; add a batch endpoint if ranges get large.
  const readiness = useQueries({
    queries: (appointments.data ?? []).map((a) => ({
      queryKey: readinessKey(a.id),
      queryFn: () => readinessApi.latest(a.id),
    })),
  });

  const rows = (appointments.data ?? []).map((a, i) => ({
    appointment: a,
    readiness: readiness[i]?.data,
  }));
  const readinessLoaded = readiness.every((q) => q.isSuccess);
  const countOf = (s: AggregateStatus) =>
    readinessLoaded ? rows.filter((r) => r.readiness?.status === s).length : undefined;
  const shown = rows.filter(
    (r) =>
      !readinessFilter ||
      (readinessFilter === 'NOT_EVALUATED'
        ? r.readiness === null
        : r.readiness?.status === readinessFilter),
  );

  return (
    <div className="space-y-6">
      <PageHeader
        title={t.title}
        description={t.description}
        actions={
          <Button asChild className="w-full sm:w-auto">
            <Link to="/agent?prepare=1">{t.cta}</Link>
          </Button>
        }
      />

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Kpi label={t.kpis.upcoming} icon={CalendarClock} value={appointments.data?.length} />
        <Kpi label={t.kpis.ready} icon={CircleCheck} value={countOf('READY')} />
        <Kpi label={t.kpis.incomplete} icon={CircleAlert} value={countOf('INCOMPLETE')} />
        <Kpi label={t.kpis.needsReview} icon={Eye} value={countOf('NEEDS_REVIEW')} />
        <Kpi label={t.kpis.pendingFollowUps} icon={ClipboardList} value={followUps.data?.length} />
      </section>

      <Card>
        <CardContent className="grid gap-4 pt-4 sm:grid-cols-2 md:pt-6 lg:grid-cols-4 lg:items-end">
          <Field id={`${id}-from`} label={t.from}>
            <Input
              id={`${id}-from`}
              type="date"
              value={toDateInput(from)}
              onChange={(e) => setParam('from', e.target.value)}
            />
          </Field>
          <Field id={`${id}-days`} label={t.days}>
            <select
              id={`${id}-days`}
              className={selectClass}
              value={days}
              onChange={(e) => setParam('days', e.target.value)}
            >
              {RANGES.map((n) => (
                <option key={n} value={n}>
                  {t.dayCount(n)}
                </option>
              ))}
            </select>
          </Field>
          <Field id={`${id}-practitioner`} label={strings.appointments.practitioner}>
            <select
              id={`${id}-practitioner`}
              className={selectClass}
              value={practitionerId}
              onChange={(e) => setParam('practitioner', e.target.value)}
            >
              <option value="">{strings.appointments.allPractitioners}</option>
              {practitioners.data?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.fullName}
                </option>
              ))}
            </select>
          </Field>
          <Field id={`${id}-readiness`} label={t.readiness}>
            <select
              id={`${id}-readiness`}
              className={selectClass}
              value={readinessFilter}
              onChange={(e) => setParam('readiness', e.target.value)}
            >
              <option value="">{t.allReadiness}</option>
              {READINESS_FILTERS.map((s) => (
                <option key={s} value={s}>
                  {strings.aggregate[s]}
                </option>
              ))}
            </select>
          </Field>
        </CardContent>
      </Card>

      {appointments.isPending ? (
        <LoadingSkeleton />
      ) : appointments.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {t.loadError}
        </p>
      ) : shown.length === 0 ? (
        <EmptyState icon={CalendarX} title={t.empty} description={t.description} />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border bg-card">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border text-xs text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">{strings.appointments.date}</th>
                <th className="px-4 py-3 font-medium">{strings.appointments.patient}</th>
                <th className="px-4 py-3 font-medium">{strings.appointments.practitioner}</th>
                <th className="px-4 py-3 font-medium">{strings.appointments.type}</th>
                <th className="px-4 py-3 font-medium">{t.readiness}</th>
                <th className="px-4 py-3 font-medium">{t.reasons}</th>
              </tr>
            </thead>
            <tbody>
              {shown.map(({ appointment: a, readiness: r }) => (
                <tr key={a.id} className="border-b border-border align-top last:border-0">
                  <td className="whitespace-nowrap px-4 py-3">
                    <Link
                      to={`/appointments/${a.id}`}
                      className="font-semibold text-primary hover:underline"
                    >
                      {formatDateTime(a.startsAt)}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <span className="block">{a.patient.fullName}</span>
                    <span className="text-xs text-muted-foreground">{a.patient.patientRef}</span>
                  </td>
                  <td className="px-4 py-3">{a.practitioner.fullName}</td>
                  <td className="px-4 py-3">{a.appointmentType.label}</td>
                  <td className="px-4 py-3">
                    {r === undefined ? '…' : <AggregateBadge status={r?.status ?? null} />}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {r && [...r.incompleteReasons, ...r.reviewReasons].join(' · ')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Kpi({ label, icon: Icon, value }: { label: string; icon: LucideIcon; value?: number }) {
  return (
    <Card role="group" aria-label={label}>
      <CardHeader className="flex-row items-center justify-between gap-2 space-y-0">
        <CardTitle>{label}</CardTitle>
        <Icon className="h-5 w-5 text-primary" aria-hidden="true" />
      </CardHeader>
      <CardContent>
        <p className="text-3xl font-semibold">{value ?? '—'}</p>
      </CardContent>
    </Card>
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
