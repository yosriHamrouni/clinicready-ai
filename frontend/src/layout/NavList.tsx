import { NavLink } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { cn } from '../lib/utils';
import { strings } from '../lib/strings';
import { Tooltip, TooltipContent, TooltipTrigger } from '../components/ui/tooltip';
import { NAV_ITEMS } from './navItems';

interface NavListProps {
  /** false = icon-only rail (labels visually hidden, shown in a tooltip). */
  showLabels: boolean;
  onNavigate?: () => void;
}

export function NavList({ showLabels, onNavigate }: NavListProps) {
  const { hasRole } = useAuth();
  const items = NAV_ITEMS.filter((i) => !i.roles || i.roles.some(hasRole));

  return (
    <nav aria-label={strings.a11y.mainNav} className="flex flex-col gap-1 px-3">
      {items.map(({ to, label, icon: Icon }) => {
        const link = (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'flex h-11 items-center gap-3 rounded-md px-3 text-sm transition-colors duration-150',
                showLabels ? '' : 'justify-center px-0',
                isActive
                  ? 'bg-primary-soft font-semibold text-primary'
                  : 'text-foreground hover:bg-accent',
              )
            }
          >
            <Icon className="h-5 w-5 shrink-0" aria-hidden="true" />
            <span className={showLabels ? '' : 'sr-only'}>{label}</span>
          </NavLink>
        );
        if (showLabels) return link;
        return (
          <Tooltip key={to}>
            <TooltipTrigger asChild>{link}</TooltipTrigger>
            <TooltipContent side="right">{label}</TooltipContent>
          </Tooltip>
        );
      })}
    </nav>
  );
}
