import { Bot } from 'lucide-react';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { strings } from '../../lib/strings';

export function AgentWorkspacePage() {
  return (
    <div className="space-y-6">
      <PageHeader title={strings.agent.title} description={strings.agent.description} />
      <EmptyState
        icon={Bot}
        title={strings.agent.emptyTitle}
        description={strings.agent.emptyDescription}
      />
    </div>
  );
}
