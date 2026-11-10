import { Users } from 'lucide-react';
import { EmptyState } from '../../components/EmptyState';
import { PageHeader } from '../../components/PageHeader';
import { strings } from '../../lib/strings';

export function PatientsPage() {
  return (
    <div className="space-y-6">
      <PageHeader title={strings.patients.title} description={strings.patients.description} />
      <EmptyState
        icon={Users}
        title={strings.patients.emptyTitle}
        description={strings.patients.emptyDescription}
      />
    </div>
  );
}
