import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { Bot } from 'lucide-react';
import { agentRunsApi } from '../../api/endpoints/agentRuns';
import { followUpsApi } from '../../api/endpoints/followUps';
import { FINISHED_RUN_STATUSES, type AgentRun } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { AggregateBadge } from '../../components/AggregateBadge';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { Badge } from '../../components/ui/badge';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { Label } from '../../components/ui/label';
import { strings } from '../../lib/strings';
import { cn } from '../../lib/utils';

const t = strings.agent;
const MAX_LENGTH = 500;
const POLL_MS = 1000;

export function AgentWorkspacePage() {
  const id = useId();
  const [params, setParams] = useSearchParams();
  const runId = params.get('run');
  const [request, setRequest] = useState('');

  const start = useMutation({
    mutationFn: (text: string) => agentRunsApi.start(text),
    onSuccess: ({ runId }) => setParams({ run: runId }, { replace: true }),
  });

  // The dashboard's "Prepare appointments" button lands here with ?prepare=1: start the same run once.
  const prepared = useRef(false);
  useEffect(() => {
    if (params.get('prepare') === '1' && !prepared.current) {
      prepared.current = true;
      setRequest(t.prepareRequest);
      start.mutate(t.prepareRequest);
    }
  }, [params, start]);

  const tooLong = request.length > MAX_LENGTH;
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!request.trim() || tooLong) return;
    start.mutate(request.trim());
  };

  return (
    <div className="space-y-6">
      <PageHeader title={t.title} description={t.description} />

      <Card>
        <CardContent className="pt-4 md:pt-6">
          <form onSubmit={submit} className="space-y-3">
            <Label htmlFor={`${id}-request`}>{t.request}</Label>
            <textarea
              id={`${id}-request`}
              rows={3}
              className="flex w-full rounded-md border border-input bg-card px-3 py-2 text-base md:text-sm"
              value={request}
              aria-describedby={`${id}-hint`}
              aria-invalid={tooLong}
              onChange={(e) => setRequest(e.target.value)}
            />
            <p id={`${id}-hint`} className="text-xs text-muted-foreground">
              {tooLong ? t.tooLong : t.requestHint}
            </p>
            <div className="flex flex-wrap gap-2" role="group" aria-label={t.examples}>
              {t.examplePrompts.map((p) => (
                <Button key={p} type="button" variant="outline" onClick={() => setRequest(p)}>
                  {p}
                </Button>
              ))}
            </div>
            <Button type="submit" disabled={start.isPending || !request.trim() || tooLong}>
              {start.isPending ? t.starting : t.start}
            </Button>
            {start.isError && (
              <p role="alert" className="text-sm text-destructive">
                {t.startError}
              </p>
            )}
          </form>
        </CardContent>
      </Card>

      {runId ? (
        <RunView runId={runId} />
      ) : (
        <EmptyState icon={Bot} title={t.emptyTitle} description={t.emptyDescription} />
      )}
    </div>
  );
}

const isFinished = (run: AgentRun | undefined) =>
  !!run && FINISHED_RUN_STATUSES.includes(run.status);

function RunView({ runId }: { runId: string }) {
  const { hasRole } = useAuth();
  const hasAuditAccess = hasRole('CLINIC_MANAGER') || hasRole('RECEPTIONIST');
  const run = useQuery({
    queryKey: ['agent-run', runId],
    queryFn: () => agentRunsApi.get(runId),
    refetchInterval: (query) => (isFinished(query.state.data) ? false : POLL_MS),
  });

  if (run.isPending) return <p className="text-sm text-muted-foreground">{strings.a11y.loading}</p>;
  if (run.isError) {
    return (
      <p role="alert" className="text-sm text-destructive">
        {t.loadError}
      </p>
    );
  }
  const r = run.data;
  const stopped = isFinished(r) && r.status !== 'SUCCEEDED';

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex-row flex-wrap items-center justify-between gap-2 space-y-0">
          <CardTitle>{t.run}</CardTitle>
          <Badge
            role="status"
            className={cn(
              r.status === 'SUCCEEDED' && 'border-green-200 bg-green-50 text-green-800',
              stopped && 'border-red-200 bg-red-50 text-red-800',
              !isFinished(r) && 'border-blue-200 bg-blue-50 text-blue-800',
            )}
          >
            {t.statuses[r.status]}
          </Badge>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm italic">« {r.request} »</p>
          {stopped && (
            <div role="alert" className="rounded-md border border-red-200 bg-red-50 p-3 text-sm">
              <p className="font-semibold text-red-800">{t.statuses[r.status]}</p>
              {r.statusReason && <p className="mt-1">{r.statusReason}</p>}
              <p className="mt-1 text-muted-foreground">{t.stoppedHint}</p>
            </div>
          )}
          <Timeline run={r} />
          {isFinished(r) && hasAuditAccess && (
            <Link to={`/audit?runId=${r.id}`} className="text-sm text-primary hover:underline">
              {t.auditTrail}
            </Link>
          )}
        </CardContent>
      </Card>
      {r.status === 'SUCCEEDED' && r.report && <Report run={r} />}
    </div>
  );
}

function Timeline({ run }: { run: AgentRun }) {
  return (
    <section aria-label={t.timeline} className="space-y-2">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-sm font-semibold">{t.timeline}</h3>
        <span className="text-xs text-muted-foreground">
          {t.totals(run.toolCalls, run.promptTokens + run.completionTokens)}
        </span>
      </div>
      {run.actions.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t.noActions}</p>
      ) : (
        <ol className="space-y-1 text-sm">
          {run.actions.map((a) => (
            <li
              key={a.seq}
              className="flex flex-wrap items-center gap-2 rounded-md border border-border px-3 py-2"
            >
              <span className="w-6 text-muted-foreground">{a.seq}.</span>
              <code className="font-mono">{a.tool}</code>
              <Badge
                className={
                  a.resultStatus === 'OK'
                    ? 'border-green-200 bg-green-50 text-green-800'
                    : 'border-red-200 bg-red-50 text-red-800'
                }
              >
                {a.resultStatus}
              </Badge>
              <span className="ml-auto text-xs text-muted-foreground">
                {t.latency(a.latencyMs)}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function Report({ run }: { run: AgentRun }) {
  const report = run.report!;
  // shortcut: filters open tasks client-side by runId; add ?runId= to GET /follow-up-tasks if lists grow.
  const followUps = useQuery({
    queryKey: ['follow-ups', 'open'],
    queryFn: () => followUpsApi.list(),
  });
  const proposed = followUps.data?.filter((f) => f.runId === run.id) ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t.report}</CardTitle>
        <p className="text-sm">{report.summary}</p>
      </CardHeader>
      <CardContent className="space-y-6">
        <dl className="grid grid-cols-3 gap-4 text-center">
          {(
            [
              [strings.dashboard.kpis.ready, report.counts.ready],
              [strings.dashboard.kpis.incomplete, report.counts.incomplete],
              [strings.dashboard.kpis.needsReview, report.counts.needsReview],
            ] as const
          ).map(([label, n]) => (
            <div key={label} className="rounded-md border border-border p-3">
              <dt className="text-xs text-muted-foreground">{label}</dt>
              <dd className="text-2xl font-semibold">{n}</dd>
            </div>
          ))}
        </dl>

        <section className="space-y-2">
          <h3 className="text-sm font-semibold">{t.findings}</h3>
          <ul className="divide-y divide-border">
            {report.appointments.map((a) => (
              <li key={a.appointmentId} className="space-y-2 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <AggregateBadge status={a.status} />
                  <Link
                    to={`/appointments/${a.appointmentId}`}
                    className="text-sm text-primary hover:underline"
                  >
                    {t.openAppointment}
                  </Link>
                </div>
                <ul className="space-y-1 text-sm">
                  {a.findings.map((f) => (
                    <li key={f.requirementCode} className="flex flex-wrap items-center gap-2">
                      <span className="font-medium">
                        {strings.documentCategories[
                          f.requirementCode as keyof typeof strings.documentCategories
                        ] ?? f.requirementCode}
                      </span>
                      <StatusBadge status={f.status} />
                      {f.action && <span className="text-muted-foreground">{f.action}</span>}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </section>

        <section className="space-y-2">
          <h3 className="text-sm font-semibold">{t.proposedFollowUps}</h3>
          {proposed.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t.noFollowUps}</p>
          ) : (
            <ul className="space-y-1 text-sm">
              {proposed.map((f) => (
                <li key={f.id} className="flex flex-wrap items-center gap-2">
                  <span>{f.suggestedAction}</span>
                  <Link to={`/follow-ups?task=${f.id}`} className="text-primary hover:underline">
                    {t.openFollowUp}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </CardContent>
    </Card>
  );
}
