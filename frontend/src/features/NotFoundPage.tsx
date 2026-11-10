import { Link } from 'react-router-dom';
import { SearchX } from 'lucide-react';
import { EmptyState } from '../components/EmptyState';
import { Button } from '../components/ui/button';
import { strings } from '../lib/strings';

export function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-xl items-center p-4">
      <div className="w-full">
        <EmptyState
          icon={SearchX}
          title={strings.notFound.title}
          description={strings.notFound.description}
          action={
            <Button asChild>
              <Link to="/dashboard">{strings.notFound.back}</Link>
            </Button>
          }
        />
      </div>
    </main>
  );
}
