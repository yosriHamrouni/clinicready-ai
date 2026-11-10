import { ListChecks } from 'lucide-react';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { strings } from '../../lib/strings';

export function FollowUpsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title={strings.followUps.title} description={strings.followUps.description} />
      <EmptyState
        icon={ListChecks}
        title={strings.followUps.emptyTitle}
        description={strings.followUps.emptyDescription}
      />
    </div>
  );
}
