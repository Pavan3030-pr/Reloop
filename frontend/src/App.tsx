import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Loading } from './components/ui';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AdminConsole } from './pages/AdminConsole';
import { CollectionPoints } from './pages/CollectionPoints';
import { CollectorWorkspace } from './pages/CollectorWorkspace';
import { Dashboard } from './pages/Dashboard';
import { History } from './pages/History';
import { Impact } from './pages/Impact';
import { Login } from './pages/Login';
import { Notifications } from './pages/Notifications';
import { PickupDetail } from './pages/PickupDetail';
import { Pickups } from './pages/Pickups';
import { Profile } from './pages/Profile';
import { Register } from './pages/Register';
import { ScanWaste } from './pages/ScanWaste';
import type { ReactNode } from 'react';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <Loading label="Restoring your session…" />
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

function RequireRole({ role, children }: { role: 'COLLECTOR' | 'ADMIN'; children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) {
    return (
      <div className="note warning">
        <span aria-hidden="true">🔒</span>
        <div>
          This area is only available to {role === 'ADMIN' ? 'platform administrators' : 'verified collectors'}. Your account is
          signed in as <strong>{user.role.toLowerCase()}</strong>.
        </div>
      </div>
    );
  }
  return <>{children}</>;
}

/** Keeps signed-in users out of the login/register screens. */
function PublicOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <Loading label="Checking your session…" />
      </div>
    );
  }
  if (user) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route
            path="/login"
            element={
              <PublicOnly>
                <Login />
              </PublicOnly>
            }
          />
          <Route
            path="/register"
            element={
              <PublicOnly>
                <Register />
              </PublicOnly>
            }
          />
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route index element={<Dashboard />} />
            <Route path="scan" element={<ScanWaste />} />
            <Route path="points" element={<CollectionPoints />} />
            <Route path="pickups" element={<Pickups />} />
            <Route path="pickups/:code" element={<PickupDetail />} />
            <Route path="history" element={<History />} />
            <Route path="impact" element={<Impact />} />
            <Route path="notifications" element={<Notifications />} />
            <Route path="profile" element={<Profile />} />
            <Route
              path="collector"
              element={
                <RequireRole role="COLLECTOR">
                  <CollectorWorkspace />
                </RequireRole>
              }
            />
            <Route
              path="admin"
              element={
                <RequireRole role="ADMIN">
                  <AdminConsole />
                </RequireRole>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
