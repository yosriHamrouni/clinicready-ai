import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '../api/endpoints/auth';
import { setAccessToken, setUnauthorizedHandler } from '../api/client';
import type { CurrentUser, LoginRequest, Role } from '../api/types';

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  user: CurrentUser | null;
  status: AuthStatus;
  login: (credentials: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  /** Display-only check (hide nav items). The backend is the source of truth. */
  hasRole: (role: Role) => boolean;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');

  const clearSession = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  // Any 401 from the API client (except login) ends the session; RequireAuth redirects.
  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    return () => setUnauthorizedHandler(null);
  }, [clearSession]);

  // Restore the session on app start.
  useEffect(() => {
    let cancelled = false;
    authApi
      .me()
      .then((me) => {
        if (cancelled) return;
        setUser(me);
        setStatus('authenticated');
      })
      .catch(() => {
        if (!cancelled) clearSession();
      });
    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const res = await authApi.login(credentials);
    setAccessToken(res.accessToken ?? null);
    setUser(res.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Clear the local session even if the server call fails.
    }
    clearSession();
  }, [clearSession]);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      login,
      logout,
      hasRole: (role) => user?.roles.includes(role) ?? false,
    }),
    [user, status, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
