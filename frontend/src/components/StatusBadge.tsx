/* eslint-disable react-refresh/only-export-components -- exports a helper next to the component */
import { Check, Clock, Eye, Minus, X, type LucideIcon } from 'lucide-react';
import { Badge } from './ui/badge';
import { cn } from '../lib/utils';
import { strings } from '../lib/strings';

export type ReadinessStatus = 'PRESENT' | 'MISSING' | 'NEEDS_REVIEW' | 'EXPIRED' | 'NOT_APPLICABLE';

export const READINESS_STATUSES: ReadinessStatus[] = [
  'PRESENT',
  'MISSING',
  'NEEDS_REVIEW',
  'EXPIRED',
  'NOT_APPLICABLE',
];

// Colour + icon + text: status is never conveyed by colour alone. All pairs meet WCAG AA.
const CONFIG: Record<ReadinessStatus, { icon: LucideIcon; className: string }> = {
  PRESENT: { icon: Check, className: 'border-green-200 bg-green-50 text-green-800' },
  MISSING: { icon: X, className: 'border-red-200 bg-red-50 text-red-800' },
  NEEDS_REVIEW: { icon: Eye, className: 'border-amber-200 bg-amber-50 text-amber-900' },
  EXPIRED: { icon: Clock, className: 'border-orange-200 bg-orange-50 text-orange-900' },
  NOT_APPLICABLE: { icon: Minus, className: 'border-slate-200 bg-slate-100 text-slate-700' },
};

export function StatusBadge({
  status,
  className,
}: {
  status: ReadinessStatus;
  className?: string;
}) {
  const { icon: Icon, className: tone } = CONFIG[status];
  return (
    <Badge className={cn(tone, className)}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {strings.status[status]}
    </Badge>
  );
}
