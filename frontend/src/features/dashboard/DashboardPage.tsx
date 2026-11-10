import { CalendarClock, CircleAlert, CircleCheck, ClipboardList } from 'lucide-react';
import { PageHeader } from '../../components/PageHeader';
import { READINESS_STATUSES, StatusBadge } from '../../components/StatusBadge';
import { Button } from '../../components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '../../components/ui/card';
import { strings } from '../../lib/strings';

const KPIS = [
  { label: strings.dashboard.kpis.upcoming, icon: CalendarClock },
  { label: strings.dashboard.kpis.ready, icon: CircleCheck },
  { label: strings.dashboard.kpis.incomplete, icon: CircleAlert },
  { label: strings.dashboard.kpis.pendingFollowUps, icon: ClipboardList },
];

const SHOW_DESIGN_DEMO = import.meta.env.VITE_SHOW_DESIGN_DEMO === 'true';

export function DashboardPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title={strings.dashboard.title}
        description={strings.dashboard.description}
        actions={
          <Button disabled className="w-full sm:w-auto">
            {strings.dashboard.cta}
          </Button>
        }
      />

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {KPIS.map(({ label, icon: Icon }) => (
          <Card key={label}>
            <CardHeader className="flex-row items-center justify-between gap-2 space-y-0">
              <CardTitle>{label}</CardTitle>
              <Icon className="h-5 w-5 text-primary" aria-hidden="true" />
            </CardHeader>
            <CardContent>
              <p className="text-3xl font-semibold">—</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {strings.dashboard.kpiPlaceholder}
              </p>
            </CardContent>
          </Card>
        ))}
      </section>

      {SHOW_DESIGN_DEMO && (
        <section
          aria-label={strings.dashboard.demoTitle}
          className="rounded-lg border border-dashed border-input bg-card p-4"
        >
          <p className="mb-3 text-xs text-muted-foreground">{strings.dashboard.demoTitle}</p>
          <div className="flex flex-wrap gap-2">
            {READINESS_STATUSES.map((s) => (
              <StatusBadge key={s} status={s} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
