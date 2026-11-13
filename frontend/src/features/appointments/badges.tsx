import { CircleCheck, CircleDashed, CircleDot } from 'lucide-react';
import type { AppointmentStatus, ChecklistItemStatus } from '../../api/types';
import { Badge } from '../../components/ui/badge';
import { cn } from '../../lib/utils';
import { strings } from '../../lib/strings';

const APPOINTMENT_TONE: Record<AppointmentStatus, string> = {
  SCHEDULED: 'border-blue-200 bg-blue-50 text-blue-800',
  COMPLETED: 'border-green-200 bg-green-50 text-green-800',
  CANCELLED: 'border-slate-200 bg-slate-100 text-slate-700',
  NO_SHOW: 'border-orange-200 bg-orange-50 text-orange-900',
};

export function AppointmentStatusBadge({ status }: { status: AppointmentStatus }) {
  return (
    <Badge className={APPOINTMENT_TONE[status]}>{strings.appointments.statuses[status]}</Badge>
  );
}

// Icon + text: status is never conveyed by colour alone.
const ITEM = {
  MISSING: { icon: CircleDashed, tone: 'border-red-200 bg-red-50 text-red-800' },
  PROVIDED: { icon: CircleDot, tone: 'border-amber-200 bg-amber-50 text-amber-900' },
  CONFIRMED: { icon: CircleCheck, tone: 'border-green-200 bg-green-50 text-green-800' },
} as const;

export function ChecklistItemBadge({
  status,
  className,
}: {
  status: ChecklistItemStatus;
  className?: string;
}) {
  const { icon: Icon, tone } = ITEM[status];
  return (
    <Badge className={cn(tone, className)}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {strings.checklist.itemStatus[status]}
    </Badge>
  );
}
