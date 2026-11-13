import type { ApiErrorBody } from './types';

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api/v1';
const DEFAULT_TIMEOUT_MS = 15_000;
/** Paths where a 401 means "bad credentials", not "session expired". */
const NO_REDIRECT_ON_401 = ['/auth/login'];

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly details?: unknown;

  constructor(init: { status: number; message: string; code?: string; details?: unknown }) {
    super(init.message);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.details = init.details;
  }
}

// --- auth wiring (registered by AuthProvider; kept out of React to avoid import cycles) ---
let accessToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

/** Option B support: in-memory JWT only. Never persisted to localStorage. */
export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

interface RequestOptions {
  timeoutMs?: number;
  signal?: AbortSignal;
}

/** Absolute URL of an API path, e.g. for <img src> / <iframe src> (the session cookie is sent). */
export function apiUrl(path: string): string {
  return buildUrl(path);
}

function buildUrl(path: string): string {
  // Absolute URL so it also works outside a browser page (tests).
  return new URL(`${BASE_URL}${path}`, window.location.origin).toString();
}

async function parseErrorBody(res: Response): Promise<ApiErrorBody> {
  try {
    const data: unknown = await res.json();
    return data && typeof data === 'object' ? (data as ApiErrorBody) : {};
  } catch {
    return {};
  }
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  { timeoutMs = DEFAULT_TIMEOUT_MS, signal }: RequestOptions = {},
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  signal?.addEventListener('abort', () => controller.abort());

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let res: Response;
  try {
    res = await fetch(buildUrl(path), {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: 'include', // Option A: httpOnly session cookie
      signal: controller.signal,
    });
  } catch (err) {
    const timedOut = err instanceof DOMException && err.name === 'AbortError';
    throw new ApiError({
      status: 0,
      code: timedOut ? 'TIMEOUT' : 'NETWORK_ERROR',
      message: timedOut ? 'La requête a expiré' : 'Erreur réseau',
    });
  } finally {
    clearTimeout(timer);
  }

  if (!res.ok) {
    const err = await parseErrorBody(res);
    if (res.status === 401 && !NO_REDIRECT_ON_401.includes(path)) {
      unauthorizedHandler?.();
    }
    throw new ApiError({
      status: res.status,
      code: err.code,
      message: err.message ?? (res.statusText || 'Erreur'),
      details: err.details,
    });
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const get = <T>(path: string, opts?: RequestOptions) =>
  request<T>('GET', path, undefined, opts);
export const post = <T, B = unknown>(path: string, body?: B, opts?: RequestOptions) =>
  request<T>('POST', path, body, opts);
export const put = <T, B = unknown>(path: string, body?: B, opts?: RequestOptions) =>
  request<T>('PUT', path, body, opts);
export const patch = <T, B = unknown>(path: string, body?: B, opts?: RequestOptions) =>
  request<T>('PATCH', path, body, opts);
export const del = <T>(path: string, opts?: RequestOptions) =>
  request<T>('DELETE', path, undefined, opts);
