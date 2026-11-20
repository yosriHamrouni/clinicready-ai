import { CircleCheck, CircleHelp, CircleX, Eye, type LucideIcon } from 'lucide-react';
import type { AggregateStatus } from '../api/types';
import { Badge } from './ui/badge';
import { strings } from '../lib/strings';

type Shown = AggregateStatus | 'NOT_EVALUATED';

// Colour + icon + text, like StatusBadge.
const CONFIG: Record<Shown, { icon: LucideIcon; className: string }> = {
  READY: { icon: CircleCheck, className: 'border-green-200 bg-green-50 text-green-800' },
  INCOMPLETE: { icon: CircleX, className: 'border-red-200 bg-red-50 text-red-800' },
  NEEDS_REVIEW: { icon: Eye, className: 'border-amber-200 bg-amber-50 text-amber-900' },
  NOT_EVALUATED: { icon: CircleHelp, className: 'border-slate-200 bg-slate-100 text-slate-700' },
};

/** Readiness of a whole appointment; null = no check has run yet. */
export function AggregateBadge({ status }: { status: AggregateStatus | null }) {
  const shown: Shown = status ?? 'NOT_EVALUATED';
  const { icon: Icon, className } = CONFIG[shown];
  return (
    <Badge className={className}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {strings.aggregate[shown]}
    </Badge>
  );
}
