import { forwardRef, type ComponentPropsWithoutRef, type ElementRef } from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '../../lib/utils';
import { strings } from '../../lib/strings';

export const Sheet = DialogPrimitive.Root;
export const SheetTrigger = DialogPrimitive.Trigger;
export const SheetClose = DialogPrimitive.Close;

export const SheetOverlay = forwardRef<
  ElementRef<typeof DialogPrimitive.Overlay>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Overlay>
>(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn(
      'fixed inset-0 z-40 bg-slate-900/50 data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in',
      className,
    )}
    {...props}
  />
));
SheetOverlay.displayName = 'SheetOverlay';

/** Drawer anchored to the inline-start edge (left in LTR, right in RTL). */
export const SheetContent = forwardRef<
  ElementRef<typeof DialogPrimitive.Content>,
  ComponentPropsWithoutRef<typeof DialogPrimitive.Content>
>(({ className, children, ...props }, ref) => (
  <DialogPrimitive.Portal>
    <SheetOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        'fixed inset-y-0 start-0 z-50 flex h-full w-72 max-w-[85vw] flex-col border-e border-border bg-card shadow-sm',
        'data-[state=open]:animate-sheet-in data-[state=closed]:animate-sheet-out',
        'rtl:data-[state=open]:animate-sheet-in-rtl rtl:data-[state=closed]:animate-sheet-out-rtl',
        className,
      )}
      {...props}
    >
      {children}
      <DialogPrimitive.Close
        className="absolute end-2 top-3 flex h-11 w-11 items-center justify-center rounded-md text-muted-foreground hover:bg-accent"
        aria-label={strings.a11y.closeMenu}
      >
        <X className="h-5 w-5" aria-hidden="true" />
      </DialogPrimitive.Close>
    </DialogPrimitive.Content>
  </DialogPrimitive.Portal>
));
SheetContent.displayName = 'SheetContent';

export const SheetTitle = DialogPrimitive.Title;
export const SheetDescription = DialogPrimitive.Description;
