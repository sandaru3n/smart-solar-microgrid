import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

export const BlockGridOperator = () => {
  const { user } = useAuth();

  if (user?.role === 'GRID_OPERATOR') {
    return <Navigate to="/dashboard/stations" replace />;
  }

  return <Outlet />;
};

export const HomeRedirect = () => {
  const { user } = useAuth();

  if (user?.role === 'GRID_OPERATOR') {
    return <Navigate to="/dashboard" replace />;
  }

  return <Navigate to="/dashboard/stations" replace />;
};

export const ProtectedRoute = ({ roles = [] }) => {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (roles.length > 0 && !roles.includes(user.role)) {
    // If they are logged in but don't have permission, maybe send them to their profile/dashboard
    if (user.role === 'BACKOFFICE') return <Navigate to="/dashboard/users/create-staff" replace />;
    if (user.role === 'GRID_OPERATOR') return <Navigate to="/dashboard" replace />;
    if (user.role === 'PROSUMER') return <Navigate to="/profile" replace />;
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
};
