import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import {
  CircleAlert,
  ClipboardCheck,
  Eye,
  EyeOff,
  Loader2,
  MessageSquareText,
  ShieldCheck,
  TriangleAlert,
} from 'lucide-react';
import { ApiError } from '../api/client';
import { LoadingSkeleton } from '../components/LoadingSkeleton';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Brand } from '../layout/Brand';
import { strings } from '../lib/strings';
import { useAuth } from './useAuth';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const VALUE_ICONS = [ClipboardCheck, MessageSquareText, ShieldCheck];

interface FieldErrors {
  email?: string;
  password?: string;
}

function validateEmail(email: string): string | undefined {
  if (!email.trim()) return strings.login.emailRequired;
  if (!EMAIL_RE.test(email.trim())) return strings.login.emailInvalid;
  return undefined;
}

function validatePassword(password: string): string | undefined {
  return password ? undefined : strings.login.passwordRequired;
}

export function LoginPage() {
  const { status, login } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string; search?: string } } | null)?.from;
  const target = from ? `${from.pathname}${from.search ?? ''}` : '/dashboard';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const emailRef = useRef<HTMLInputElement>(null);
  const alertRef = useRef<HTMLDivElement>(null);

  // Focus the e-mail field once the form is on screen.
  useEffect(() => {
    if (status === 'anonymous') emailRef.current?.focus();
  }, [status]);

  // Move focus to the error banner after a failed attempt.
  useEffect(() => {
    if (formError) alertRef.current?.focus();
  }, [formError]);

  if (status === 'loading') return <LoadingSkeleton fullScreen />;
  if (status === 'authenticated') return <Navigate to={target} replace />;

  function trackCapsLock(e: KeyboardEvent<HTMLInputElement>) {
    setCapsLock(e.getModifierState('CapsLock'));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    const found: FieldErrors = {
      email: validateEmail(email),
      password: validatePassword(password),
    };
    setErrors(found);
    if (found.email || found.password) return;

    setPending(true);
    try {
      await login({ email: email.trim(), password });
      // On success the status flips to "authenticated" and <Navigate> above redirects.
    } catch (err) {
      setFormError(
        err instanceof ApiError && err.status === 401
          ? strings.login.invalidCredentials
          : strings.login.serviceError,
      );
      setPending(false);
    }
  }

  return (
    <div className="min-h-screen bg-background lg:grid lg:grid-cols-2">
      {/* Brand panel (desktop only) */}
      <aside className="hidden flex-col justify-between bg-gradient-to-br from-teal-900 to-teal-700 p-12 text-white lg:flex">
        <Brand inverted />
        <div className="max-w-md">
          <p className="text-3xl font-semibold leading-tight">{strings.app.tagline}</p>
          <ul className="mt-10 space-y-6">
            {strings.login.values.map((v, i) => {
              const Icon = VALUE_ICONS[i]!;
              return (
                <li key={v.title} className="flex gap-4">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-white/15">
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </span>
                  <div>
                    <p className="font-semibold">{v.title}</p>
                    <p className="text-sm text-white/90">{v.text}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
        <p className="text-sm text-white/80">{strings.login.footer}</p>
      </aside>

      {/* Form column */}
      <main className="flex min-h-screen flex-col px-4 py-8 lg:min-h-0 lg:px-8">
        <Brand className="lg:hidden" />
        <div className="flex flex-1 items-center justify-center py-8">
          <Card className="w-full max-w-md p-6 md:p-8">
            <form onSubmit={onSubmit} noValidate className="space-y-5">
              <div>
                <h1 className="text-2xl">{strings.login.heading}</h1>
                <p className="mt-1 text-sm text-muted-foreground">{strings.login.subheading}</p>
              </div>

              {formError && (
                <div
                  ref={alertRef}
                  role="alert"
                  tabIndex={-1}
                  className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-3 text-sm text-red-800"
                >
                  <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  <span>{formError}</span>
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="email">{strings.login.email}</Label>
                <Input
                  ref={emailRef}
                  id="email"
                  type="email"
                  autoComplete="username"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  onBlur={() => setErrors((p) => ({ ...p, email: validateEmail(email) }))}
                  aria-invalid={!!errors.email}
                  aria-describedby={errors.email ? 'email-error' : undefined}
                />
                {errors.email && (
                  <p id="email-error" className="text-sm text-destructive">
                    {errors.email}
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">{strings.login.password}</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    className="pe-12"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    onKeyDown={trackCapsLock}
                    onKeyUp={trackCapsLock}
                    onBlur={() => {
                      setCapsLock(false);
                      setErrors((p) => ({ ...p, password: validatePassword(password) }));
                    }}
                    aria-invalid={!!errors.password}
                    aria-describedby={
                      [errors.password && 'password-error', capsLock && 'caps-warning']
                        .filter(Boolean)
                        .join(' ') || undefined
                    }
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="absolute end-0 top-0 text-muted-foreground"
                    onClick={() => setShowPassword((v) => !v)}
                    aria-label={
                      showPassword ? strings.login.hidePassword : strings.login.showPassword
                    }
                    aria-pressed={showPassword}
                  >
                    {showPassword ? (
                      <EyeOff className="h-5 w-5" aria-hidden="true" />
                    ) : (
                      <Eye className="h-5 w-5" aria-hidden="true" />
                    )}
                  </Button>
                </div>
                {capsLock && (
                  <p id="caps-warning" className="flex items-center gap-1 text-sm text-amber-900">
                    <TriangleAlert className="h-4 w-4" aria-hidden="true" />
                    {strings.login.capsLock}
                  </p>
                )}
                {errors.password && (
                  <p id="password-error" className="text-sm text-destructive">
                    {errors.password}
                  </p>
                )}
              </div>

              <Button type="submit" disabled={pending} className="w-full">
                {pending && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                {pending ? strings.login.pending : strings.login.submit}
              </Button>
            </form>
          </Card>
        </div>
        <p className="text-center text-xs text-muted-foreground lg:hidden">
          {strings.login.footer}
        </p>
      </main>
    </div>
  );
}
