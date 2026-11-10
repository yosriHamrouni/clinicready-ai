import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import { LoadingSkeleton } from '../components/LoadingSkeleton';

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') return <LoadingSkeleton fullScreen />;
  if (status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <Outlet />;
}
