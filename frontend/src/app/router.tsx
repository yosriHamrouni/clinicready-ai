import { Navigate, createBrowserRouter, type RouteObject } from 'react-router-dom';
import { LoginPage } from '../auth/LoginPage';
import { RequireAuth } from '../auth/RequireAuth';
import { AppLayout } from '../layout/AppLayout';
import { DashboardPage } from '../features/dashboard/DashboardPage';
import { PatientsPage } from '../features/patients/PatientsPage';
import { PatientDetailPage } from '../features/patients/PatientDetailPage';
import { AppointmentsPage } from '../features/appointments/AppointmentsPage';
import { AppointmentDetailPage } from '../features/appointments/AppointmentDetailPage';
import { AgentWorkspacePage } from '../features/agent/AgentWorkspacePage';
import { FollowUpsPage } from '../features/followups/FollowUpsPage';
import { RequirementRulesPage } from '../features/settings/RequirementRulesPage';
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
          { path: '/patients/:id', element: <PatientDetailPage /> },
          { path: '/appointments', element: <AppointmentsPage /> },
          { path: '/appointments/:id', element: <AppointmentDetailPage /> },
          { path: '/agent', element: <AgentWorkspacePage /> },
          { path: '/follow-ups', element: <FollowUpsPage /> },
          { path: '/settings/requirements', element: <RequirementRulesPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];

export const createAppRouter = () => createBrowserRouter(routes);
