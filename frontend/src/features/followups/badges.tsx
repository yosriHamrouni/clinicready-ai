import type { FollowUpStatus } from '../../api/types';
import { Badge } from '../../components/ui/badge';
import { strings } from '../../lib/strings';
import { cn } from '../../lib/utils';

const TONES: Record<FollowUpStatus, string> = {
  PROPOSED: 'border-slate-200 bg-slate-100 text-slate-700',
  AWAITING_APPROVAL: 'border-amber-200 bg-amber-50 text-amber-900',
  APPROVED: 'border-blue-200 bg-blue-50 text-blue-800',
  REJECTED: 'border-red-200 bg-red-50 text-red-800',
  DELIVERED: 'border-teal-200 bg-teal-50 text-teal-900',
  RESOLVED: 'border-green-200 bg-green-50 text-green-800',
};

/** Colour plus the state's name: never colour alone. */
export function FollowUpStatusBadge({ status }: { status: FollowUpStatus }) {
  return <Badge className={cn(TONES[status])}>{strings.followUps.statuses[status]}</Badge>;
}
