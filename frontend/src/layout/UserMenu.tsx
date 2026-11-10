/* eslint-disable react-refresh/only-export-components -- exports a helper next to the component */
import { Building2, ChevronDown, LogOut } from 'lucide-react';
import { useAuth } from '../auth/useAuth';
import { strings } from '../lib/strings';
import { Avatar, AvatarFallback } from '../components/ui/avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '../components/ui/dropdown-menu';

export function initials(fullName: string): string {
  return (
    fullName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]!.toUpperCase())
      .join('') || '?'
  );
}

export function UserMenu() {
  const { user, logout } = useAuth();
  if (!user) return null;
  const roleLabels = user.roles.map((r) => strings.user.roles[r]).join(', ');

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`${strings.a11y.userMenu} — ${user.fullName}`}
        className="flex h-11 items-center gap-1 rounded-md px-1 hover:bg-accent"
      >
        <Avatar>
          <AvatarFallback>{initials(user.fullName)}</AvatarFallback>
        </Avatar>
        <ChevronDown className="hidden h-4 w-4 text-muted-foreground md:block" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuLabel>
          <div className="font-semibold">{user.fullName}</div>
          <div className="text-xs text-muted-foreground">{roleLabels}</div>
          <div className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
            <Building2 className="h-3.5 w-3.5" aria-hidden="true" />
            {strings.user.clinic} : {user.clinicId}
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void logout()}>
          <LogOut className="h-4 w-4" aria-hidden="true" />
          {strings.user.logout}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
