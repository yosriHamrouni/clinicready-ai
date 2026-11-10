import { Navigate, createBrowserRouter, type RouteObject } from 'react-router-dom';
import { LoginPage } from '../auth/LoginPage';
import { RequireAuth } from '../auth/RequireAuth';
import { AppLayout } from '../layout/AppLayout';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { PatientsPage } from '../features/patients/PatientsPage';
import { AgentWorkspacePage } from '../features/agent/AgentWorkspacePage';
import { FollowUpsPage } from '../features/followups/FollowUpsPage';
import { NotFoundPage } from '../features/NotFoundPage';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <Navigate to="/dashboard" replace /> },
          { path: '/dashboard', element: <DashboardPage /> },
          { path: '/patients', element: <PatientsPage /> },
          { path: '/agent', element: <AgentWorkspacePage /> },
          { path: '/follow-ups', element: <FollowUpsPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];

export const createAppRouter = () => createBrowserRouter(routes);
