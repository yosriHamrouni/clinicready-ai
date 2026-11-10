import { Skeleton } from './ui/skeleton';
import { strings } from '../lib/strings';

/** Page-level loading placeholder. Announced once to screen readers. */
export function LoadingSkeleton({ fullScreen = false }: { fullScreen?: boolean }) {
  return (
    <div role="status" className={fullScreen ? 'min-h-screen p-4 md:p-8' : 'p-0'}>
      <span className="sr-only">{strings.a11y.loading}</span>
      <div className="mx-auto w-full max-w-[1280px] space-y-6">
        <div className="space-y-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-72 max-w-full" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => (
            <Skeleton key={i} className="h-28 rounded-lg" />
          ))}
        </div>
      </div>
    </div>
  );
}
