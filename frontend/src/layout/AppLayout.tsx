import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Menu, PanelLeftClose, PanelLeftOpen, Search } from 'lucide-react';
import { useAuth } from '../auth/useAuth';
import { strings } from '../lib/strings';
import { cn } from '../lib/utils';
import { useMediaQuery } from '../lib/useMediaQuery';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '../components/ui/sheet';
import { TooltipProvider } from '../components/ui/tooltip';
import { Brand } from './Brand';
import { NavList } from './NavList';
import { NAV_ITEMS } from './navItems';
import { UserMenu } from './UserMenu';
import { useSidebarCollapsed } from './useSidebarCollapsed';

/**
 * Breakpoints (mobile-first):
 *  < md (768)  : top bar + off-canvas drawer
 *  md–lg       : 64px icon rail
 *  >= lg (1024): 240px sidebar, collapsible to the rail
 */
export function AppLayout() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const isDesktop = useMediaQuery('(min-width: 1024px)');
  const [collapsed, toggleCollapsed] = useSidebarCollapsed();
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Close the drawer whenever the route changes.
  useEffect(() => setDrawerOpen(false), [pathname]);

  const showLabels = isDesktop && !collapsed;
  const pageTitle = NAV_ITEMS.find((i) => pathname.startsWith(i.to))?.label ?? '';
  const roleLabel = user?.roles.map((r) => strings.user.roles[r]).join(', ');

  return (
    <TooltipProvider delayDuration={200}>
      <a
        href="#main-content"
        className="sr-only z-[60] rounded-md bg-primary px-4 py-3 text-primary-foreground focus:not-sr-only focus:fixed focus:start-4 focus:top-4"
      >
        {strings.a11y.skipToContent}
      </a>

      {/* Desktop / tablet sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 start-0 z-30 hidden flex-col border-e border-border bg-card md:flex',
          'md:w-[var(--rail-width)]',
          collapsed ? 'lg:w-[var(--rail-width)]' : 'lg:w-[var(--sidebar-width)]',
        )}
      >
        <div className={cn('flex h-16 items-center px-4', !showLabels && 'justify-center px-0')}>
          <Brand showName={showLabels} />
        </div>
        <div className="flex-1 overflow-y-auto py-2">
          <NavList showLabels={showLabels} />
        </div>
        <div className="border-t border-border p-3">
          {showLabels && user && (
            <div className="mb-3 min-w-0 px-1">
              <div className="truncate text-sm font-semibold">{user.fullName}</div>
              <div className="truncate text-xs text-muted-foreground">{roleLabel}</div>
            </div>
          )}
          <Button
            variant="ghost"
            size="icon"
            className="hidden lg:inline-flex"
            onClick={toggleCollapsed}
            aria-label={collapsed ? strings.a11y.expandSidebar : strings.a11y.collapseSidebar}
            aria-expanded={!collapsed}
          >
            {collapsed ? (
              <PanelLeftOpen className="h-5 w-5 rtl:-scale-x-100" aria-hidden="true" />
            ) : (
              <PanelLeftClose className="h-5 w-5 rtl:-scale-x-100" aria-hidden="true" />
            )}
          </Button>
        </div>
      </aside>

      {/* Mobile drawer */}
      <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
        <SheetContent>
          <SheetTitle className="sr-only">{strings.a11y.mainNav}</SheetTitle>
          <SheetDescription className="sr-only">{strings.app.tagline}</SheetDescription>
          <div className="flex h-16 items-center px-4">
            <Brand />
          </div>
          <NavList showLabels onNavigate={() => setDrawerOpen(false)} />
        </SheetContent>
      </Sheet>

      <div
        className={cn(
          'flex min-h-screen flex-col md:ps-[var(--rail-width)]',
          collapsed ? 'lg:ps-[var(--rail-width)]' : 'lg:ps-[var(--sidebar-width)]',
        )}
      >
        {/* Mobile top bar */}
        <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-border bg-card px-2 md:hidden">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setDrawerOpen(true)}
            aria-label={strings.a11y.openMenu}
            aria-expanded={drawerOpen}
          >
            <Menu className="h-5 w-5" aria-hidden="true" />
          </Button>
          <Brand className="flex-1" />
          <UserMenu />
        </header>

        {/* Tablet / desktop header */}
        <header className="sticky top-0 z-20 hidden h-16 items-center gap-4 border-b border-border bg-card px-6 md:flex lg:px-8">
          <p className="min-w-0 flex-1 truncate text-sm text-muted-foreground">{pageTitle}</p>
          <div className="relative w-full max-w-xs">
            <Search
              className="pointer-events-none absolute start-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              disabled
              type="search"
              className="ps-9"
              placeholder={strings.header.searchSoon}
              aria-label={strings.header.search}
            />
          </div>
          <UserMenu />
        </header>

        <main
          id="main-content"
          tabIndex={-1}
          className="mx-auto w-full max-w-[1280px] flex-1 p-4 outline-none md:p-6 lg:p-8"
        >
          <Outlet />
        </main>
      </div>
    </TooltipProvider>
  );
}
