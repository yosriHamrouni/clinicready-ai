import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { readinessApi, readinessKey } from '../../api/endpoints/readiness';
import type { ReadinessCheck } from '../../api/types';
import { AggregateBadge } from '../../components/AggregateBadge';
import { StatusBadge } from '../../components/StatusBadge';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { formatDateTime } from '../../lib/dates';
import { strings } from '../../lib/strings';

const r = strings.readiness;

/** Checklist of one appointment with each requirement's reason and evidence. */
export function ReadinessPanel({
  appointmentId,
  patientId,
  canRun,
}: {
  appointmentId: string;
  patientId: string;
  canRun: boolean;
}) {
  const queryClient = useQueryClient();
  const readiness = useQuery({
    queryKey: readinessKey(appointmentId),
    queryFn: () => readinessApi.latest(appointmentId),
  });
  const run = useMutation({
    mutationFn: () => readinessApi.run(appointmentId),
    onSuccess: (data) => queryClient.setQueryData(readinessKey(appointmentId), data),
  });

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-2 space-y-0">
        <CardTitle>{r.title}</CardTitle>
        <div className="flex items-center gap-2">
          {readiness.isSuccess && <AggregateBadge status={readiness.data?.status ?? null} />}
          {canRun && (
            <Button variant="outline" disabled={run.isPending} onClick={() => run.mutate()}>
              {run.isPending ? r.running : r.run}
            </Button>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {run.isError && (
          <p role="alert" className="text-sm text-destructive">
            {r.runError}
          </p>
        )}
        {readiness.isPending ? (
          <p className="text-sm text-muted-foreground">{strings.a11y.loading}</p>
        ) : readiness.isError ? (
          <p role="alert" className="text-sm text-destructive">
            {r.loadError}
          </p>
        ) : readiness.data === null ? (
          <p className="text-sm text-muted-foreground">{r.notEvaluated}</p>
        ) : (
          <>
            <p className="text-xs text-muted-foreground">
              {readiness.data.notice} · {r.evaluatedAt(formatDateTime(readiness.data.evaluatedAt))}
            </p>
            <ul className="divide-y divide-border">
              {readiness.data.checks.map((check, i) => (
                <CheckRow
                  key={check.ruleId ?? `${check.requirement}-${i}`}
                  check={check}
                  patientId={patientId}
                />
              ))}
            </ul>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function CheckRow({ check, patientId }: { check: ReadinessCheck; patientId: string }) {
  const label =
    check.requirement === 'DOCUMENT'
      ? check.documentCategories.map((c) => strings.documentCategories[c]).join(' / ')
      : r.requirements[check.requirement];
  return (
    <li className="space-y-1 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{label}</span>
        <span className="text-xs text-muted-foreground">
          {check.mandatory ? r.mandatory : r.optional}
        </span>
        <StatusBadge status={check.status} />
      </div>
      <p className="text-sm">{check.reason}</p>
      {check.remainingAction && (
        <p className="text-sm text-muted-foreground">
          {r.nextStep} : {check.remainingAction}
        </p>
      )}
      {check.requirement === 'DOCUMENT' && (
        <p className="text-sm">
          {r.evidence} :{' '}
          {check.documents.length === 0
            ? r.noEvidence
            : check.documents.map((d, i) => (
                <span key={d.documentId}>
                  {i > 0 && ', '}
                  <Link
                    to={`/patients/${patientId}?document=${d.documentId}`}
                    className="text-primary hover:underline"
                  >
                    {strings.documentCategories[d.category]} v{d.version}
                  </Link>
                </span>
              ))}
        </p>
      )}
      {check.confirmations.map((c) => (
        <p key={c.type} className="text-xs text-muted-foreground">
          {r.confirmedBy(c.confirmedByName ?? c.confirmedBy, formatDateTime(c.confirmedAt))}
        </p>
      ))}
    </li>
  );
}
