import { Stethoscope } from 'lucide-react';
import { strings } from '../lib/strings';
import { cn } from '../lib/utils';

export function Brand({
  showName = true,
  inverted = false,
  className,
}: {
  showName?: boolean;
  inverted?: boolean;
  className?: string;
}) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <span
        className={cn(
          'flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
          inverted ? 'bg-white/15 text-white' : 'bg-primary text-primary-foreground',
        )}
      >
        <Stethoscope className="h-5 w-5" aria-hidden="true" />
      </span>
      <span
        className={cn(
          'text-lg font-semibold',
          inverted ? 'text-white' : 'text-foreground',
          !showName && 'sr-only',
        )}
      >
        {strings.app.name}
      </span>
    </div>
  );
}
