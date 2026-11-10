import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '../../lib/utils';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type, ...props }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        'flex h-11 w-full rounded-md border border-input bg-card px-3 text-base text-foreground transition-colors duration-150 placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-80 aria-[invalid=true]:border-destructive md:text-sm',
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = 'Input';
