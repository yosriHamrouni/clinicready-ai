import { Bot, FileCheck, LayoutDashboard, ListChecks, Users, type LucideIcon } from 'lucide-react';
import type { Role } from '../api/types';
import { strings } from '../lib/strings';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Display-only filter; omit to show to everyone. Backend enforces real access. */
  roles?: Role[];
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/dashboard', label: strings.nav.dashboard, icon: LayoutDashboard },
  { to: '/patients', label: strings.nav.patients, icon: Users },
  { to: '/agent', label: strings.nav.agent, icon: Bot },
  { to: '/follow-ups', label: strings.nav.followUps, icon: ListChecks },
  {
    to: '/settings/requirements',
    label: strings.nav.requirements,
    icon: FileCheck,
    roles: ['CLINIC_MANAGER'],
  },
];
