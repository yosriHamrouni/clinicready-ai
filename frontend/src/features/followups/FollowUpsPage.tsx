import { useQuery } from '@tanstack/react-query';
import { ListChecks } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { followUpsApi } from '../../api/endpoints/followUps';
import type { FollowUpStatus } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { strings } from '../../lib/strings';
import { frenchReason } from '../../lib/reasons';
import { cn } from '../../lib/utils';
import { FollowUpDetail } from './FollowUpDetail';
import { FollowUpStatusBadge } from './badges';
import { requirementLabel } from './labels';

const t = strings.followUps;

/** "open" is the server default: every task not yet resolved or rejected. */
type Filter = 'open' | Exclude<FollowUpStatus, 'PROPOSED'>;
const FILTERS: Filter[] = ['open', 'AWAITING_APPROVAL', 'APPROVED', 'DELIVERED', 'RESOLVED', 'REJECTED'];

const isFilter = (value: string | null): value is Filter =>
  value !== null && (FILTERS as string[]).includes(value);

export function FollowUpsPage() {
  const [params, setParams] = useSearchParams();
  const filter: Filter = isFilter(params.get('status')) ? (params.get('status') as Filter) : 'open';
  const selected = params.get('task');

  const tasks = useQuery({
    queryKey: ['follow-ups', filter],
    queryFn: () => followUpsApi.list(filter === 'open' ? [] : [filter]),
  });

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) next.delete(key);
      else next.set(key, value);
    }
    setParams(next);
  };

  return (
    <div className="space-y-6">
      <PageHeader title={t.title} description={t.description} />

      <div role="group" aria-label={t.filterLabel} className="flex flex-wrap gap-2">
        {FILTERS.map((f) => (
          <button
            key={f}
            type="button"
            aria-pressed={filter === f}
            onClick={() => update({ status: f === 'open' ? null : f, task: null })}
            className={cn(
              'h-11 rounded-full border px-4 text-sm font-medium transition-colors',
              filter === f
                ? 'border-primary bg-primary text-primary-foreground'
                : 'border-input bg-card hover:bg-accent',
            )}
          >
            {t.filters[f]}
          </button>
        ))}
      </div>

      {tasks.isPending && <p className="text-sm text-muted-foreground">{strings.a11y.loading}</p>}
      {tasks.isError && (
        <p role="alert" className="text-sm text-destructive">
          {t.loadError}
        </p>
      )}
      {tasks.data && tasks.data.length === 0 && !selected && (
        <EmptyState icon={ListChecks} title={t.emptyTitle} description={t.emptyDescription} />
      )}
      {tasks.data && (tasks.data.length > 0 || selected) && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
          <section aria-label={t.list}>
            <ul className="space-y-2">
              {tasks.data.map((task) => (
                <li key={task.id}>
                  <button
                    type="button"
                    aria-current={selected === task.id}
                    onClick={() => update({ task: task.id })}
                    className={cn(
                      'w-full rounded-lg border bg-card p-4 text-left transition-colors hover:bg-accent',
                      selected === task.id ? 'border-primary' : 'border-border',
                    )}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium">{requirementLabel(task.requirementCode)}</span>
                      <FollowUpStatusBadge status={task.status} />
                    </div>
                    <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{frenchReason(task.reason)}</p>
                    {task.dueAt && (
                      <p className="mt-1 text-xs text-muted-foreground">
                        {t.dueOn(new Date(task.dueAt).toLocaleDateString('fr-FR'))}
                      </p>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          </section>
          <section aria-label={strings.followUps.draftTitle} className="min-w-0">
            {selected ? (
              <FollowUpDetail key={selected} taskId={selected} />
            ) : (
              <p className="text-sm text-muted-foreground">{t.selectPrompt}</p>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
