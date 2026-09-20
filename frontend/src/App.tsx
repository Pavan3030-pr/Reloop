import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Loading, Note } from './components/ui';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AdminConsole } from './pages/AdminConsole';
import { CollectionPoints } from './pages/CollectionPoints';
import { CollectorWorkspace } from './pages/CollectorWorkspace';
import { Dashboard } from './pages/Dashboard';
import { History } from './pages/History';
import { Impact } from './pages/Impact';
import { Landing } from './pages/Landing';
import { Legal } from './pages/Legal';
import { Login } from './pages/Login';
import { Notifications } from './pages/Notifications';
import { PickupDetail } from './pages/PickupDetail';
import { Pickups } from './pages/Pickups';
import { Profile } from './pages/Profile';
import { Register } from './pages/Register';
import { ScanWaste } from './pages/ScanWaste';
import type { ReactNode } from 'react';

function SessionCheck({ label }: { label: string }) {
  return (
    <div style={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
      <Loading label={label} />
    </div>
  );
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <SessionCheck label="Restoring your session…" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

function RequireRole({ role, children }: { role: 'COLLECTOR' | 'ADMIN'; children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== role) {
    return (
      <Note tone="warning">
        <div>
          <strong>
            {role === 'ADMIN' ? 'Administrators only' : 'Verified collectors only'}
          </strong>
          <div className="small" style={{ marginTop: 4 }}>
            You are signed in as {user.role.toLowerCase()}. {role === 'COLLECTOR'
              ? 'Apply to collect from your profile and an administrator will review it.'
              : 'Ask an administrator for access.'}
          </div>
        </div>
      </Note>
    );
  }
  return <>{children}</>;
}

/** Keeps signed-in users out of the login/register screens. */
function PublicOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <SessionCheck label="Checking your session…" />;
  if (user) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

/** The landing page is public; signed-in visitors land on their dashboard. */
function Home() {
  const { user, loading } = useAuth();
  if (loading) return <SessionCheck label="Loading ReLoop…" />;
  if (user) return <Navigate to="/dashboard" replace />;
  return <Landing />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/privacy" element={<Legal kind="privacy" />} />
          <Route path="/terms" element={<Legal kind="terms" />} />
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
            <Route path="dashboard" element={<Dashboard />} />
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
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
