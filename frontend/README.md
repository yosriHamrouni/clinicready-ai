# Frontend

React 18 · TypeScript (strict) · Vite · react-router-dom v6 · React Query · Tailwind · MSW · Vitest

Scaffolded in **YOS-9**. This is the app shell only (routing, API client, auth, layout); the business pages are placeholders.

## Run

Requires Node 20+.

```bash
cd frontend
npm install
cp .env.example .env
```

### 1. Mock mode (no backend)

Set `VITE_USE_MOCKS=true` in `.env`, then `npm run dev` → http://localhost:5173.
Log in with `reception@clinic.test` / `password` (anything else returns 401).

### 2. Against a local API (no Docker)

Keep `VITE_USE_MOCKS=false` and start the API on port 8080 (or set `VITE_DEV_API_TARGET`).
`npm run dev` proxies `/api` → `http://localhost:8080`, so the browser stays same-origin.

### 3. Docker Compose

From the repo root:

```bash
docker compose up --build frontend   # http://localhost:3000 (FRONTEND_PORT to change)
```

nginx serves the SPA (fallback to `index.html`) and proxies `/api/` → `http://api:8080`.
`depends_on: api` is commented out in `docker-compose.yml` until the `api` service exists (YOS-8); uncomment it then.

## Scripts

`dev` · `build` · `preview` · `test` · `lint` · `format` · `typecheck`

## Environment variables

| Variable              | Default                 | Purpose                                 |
| --------------------- | ----------------------- | --------------------------------------- |
| `VITE_API_BASE_URL`   | `/api/v1`               | API base path (build-time)              |
| `VITE_USE_MOCKS`      | `false`                 | `true` starts MSW in the browser        |
| `VITE_DEV_API_TARGET` | `http://localhost:8080` | Vite dev-server proxy target for `/api` |

## Auth

The API client sends `credentials: 'include'` (httpOnly session cookie, no token in JS). If the login response contains an `accessToken`, it is kept **in memory only** and sent as `Authorization: Bearer …`. Any 401 (except from `/auth/login`) clears the session and `RequireAuth` redirects to `/login`. Role checks in the UI are display-only.

## Structure

```
src/
├── app/          router.tsx, providers.tsx, App.tsx
├── api/          client.ts (typed fetch wrapper), types.ts, endpoints/auth.ts
├── auth/         AuthContext, useAuth, RequireAuth, LoginPage
├── layout/       AppLayout (sidebar + header + Outlet)
├── features/     dashboard/, patients/, agent/, followups/ (placeholders)
├── mocks/        MSW handlers, browser worker, node server (tests)
└── test/         Vitest setup + tests
```

See [CONTRIBUTING.md](../CONTRIBUTING.md) for frontend rules.
