import { useQuery } from '@tanstack/react-query';
import { ScrollText } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { auditApi } from '../../api/endpoints/audit';
import { useAuth } from '../../auth/useAuth';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { Button } from '../../components/ui/button';
import { strings } from '../../lib/strings';

const t = strings.audit;
const PAGE_SIZE = 25;

const formatWhen = (iso: string) =>
  new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });

/** "tool: checkReadiness · seq: 2": ids and field values only, as the backend guarantees. */
const formatDetails = (summary: Record<string, unknown>) =>
  Object.entries(summary)
    .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
    .join(' · ');

export function AuditPage() {
  const { hasRole } = useAuth();
  const [params, setParams] = useSearchParams();
  const runId = params.get('runId') ?? undefined;
  const page = Math.max(0, Number(params.get('page')) || 0);

  const events = useQuery({
    queryKey: ['audit-events', runId, page],
    queryFn: () => auditApi.list({ runId, page, size: PAGE_SIZE }),
  });

  const goToPage = (next: number) => {
    const copy = new URLSearchParams(params);
    if (next > 0) copy.set('page', String(next));
    else copy.delete('page');
    setParams(copy);
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={runId ? t.forRun : t.title}
        description={hasRole('CLINIC_MANAGER') ? t.description : t.descriptionOwn}
        actions={
          runId && (
            <Button asChild variant="outline">
              <Link to="/audit">{t.showAll}</Link>
            </Button>
          )
        }
      />

      {events.isPending && <p className="text-sm text-muted-foreground">{strings.a11y.loading}</p>}
      {events.isError && (
        <p role="alert" className="text-sm text-destructive">
          {t.loadError}
        </p>
      )}
      {events.data && events.data.items.length === 0 && (
        <EmptyState icon={ScrollText} title={t.emptyTitle} description={t.emptyDescription} />
      )}
      {events.data && events.data.items.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border border-border bg-card">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border text-xs text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">{t.columns.when}</th>
                  <th className="px-4 py-3 font-medium">{t.columns.action}</th>
                  <th className="px-4 py-3 font-medium">{t.columns.object}</th>
                  <th className="px-4 py-3 font-medium">{t.columns.author}</th>
                  <th className="px-4 py-3 font-medium">{t.columns.details}</th>
                </tr>
              </thead>
              <tbody>
                {events.data.items.map((e) => (
                  <tr key={e.id} className="border-b border-border align-top last:border-0">
                    <td className="whitespace-nowrap px-4 py-3">{formatWhen(e.occurredAt)}</td>
                    <td className="px-4 py-3">{t.actions[e.action] ?? e.action}</td>
                    <td className="px-4 py-3">{t.entities[e.entityType] ?? e.entityType}</td>
                    <td className="px-4 py-3">{e.actorName ?? t.system}</td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                      {formatDetails(e.changeSummary)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pager
            page={events.data.page}
            total={events.data.total}
            size={events.data.size}
            onPage={goToPage}
          />
        </>
      )}
    </div>
  );
}

function Pager(props: { page: number; total: number; size: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(props.total / props.size));
  return (
    <div className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
      <span>{t.pageOf(props.page + 1, pages, props.total)}</span>
      <div className="flex gap-2">
        <Button
          variant="outline"
          disabled={props.page <= 0}
          onClick={() => props.onPage(props.page - 1)}
        >
          {t.previous}
        </Button>
        <Button
          variant="outline"
          disabled={props.page + 1 >= pages}
          onClick={() => props.onPage(props.page + 1)}
        >
          {t.next}
        </Button>
      </div>
    </div>
  );
}
