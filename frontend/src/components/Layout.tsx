import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../context/AuthContext';

interface NavItem {
  to: string;
  label: string;
  icon: string;
  end?: boolean;
}

const RESIDENT_NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: '🏠', end: true },
  { to: '/scan', label: 'Scan waste', icon: '📷' },
  { to: '/points', label: 'Collection points', icon: '📍' },
  { to: '/pickups', label: 'My pickups', icon: '🚚' },
  { to: '/history', label: 'Recycling history', icon: '🗂️' },
  { to: '/impact', label: 'My impact', icon: '🌍' },
];

const ACCOUNT_NAV: NavItem[] = [
  { to: '/notifications', label: 'Notifications', icon: '🔔' },
  { to: '/profile', label: 'Profile', icon: '👤' },
];

const TITLES: Record<string, { title: string; sub: string }> = {
  '/': { title: 'Dashboard', sub: 'Your recycling at a glance' },
  '/scan': { title: 'Scan waste', sub: 'Identify an item and record it' },
  '/points': { title: 'Collection points', sub: 'Verified places that accept your materials' },
  '/pickups': { title: 'My pickups', sub: 'Track your pickup requests' },
  '/history': { title: 'Recycling history', sub: 'Everything you have actually recycled' },
  '/impact': { title: 'My impact', sub: 'Estimated environmental benefit of your collections' },
  '/notifications': { title: 'Notifications', sub: 'Updates about your pickups and account' },
  '/profile': { title: 'Profile', sub: 'Your details and collector application' },
  '/collector': { title: 'Collector workspace', sub: 'Accept jobs, record weights, close the loop' },
  '/admin': { title: 'Admin console', sub: 'Directory, catalog and platform analytics' },
};

export function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    let active = true;
    const load = () =>
      api
        .unreadCount()
        .then((result) => {
          if (active) setUnread(result.count);
        })
        .catch(() => undefined);
    load();
    const timer = window.setInterval(load, 30000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [location.pathname]);

  const meta = TITLES[location.pathname] ??
    (location.pathname.startsWith('/pickups/') ? { title: 'Pickup details', sub: 'Status and timeline' } : { title: 'ReLoop', sub: '' });

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const renderLink = (item: NavItem) => (
    <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
      <span className="nav-icon" aria-hidden="true">
        {item.icon}
      </span>
      <span>{item.label}</span>
      {item.to === '/notifications' && unread > 0 ? <span className="nav-count">{unread}</span> : null}
    </NavLink>
  );

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            ♻
          </span>
          <span>
            <span className="brand-name">ReLoop</span>
            <span className="brand-tag">Circular waste</span>
          </span>
        </div>

        <nav aria-label="Main">
          <div className="nav-section">Recycle</div>
          {RESIDENT_NAV.map(renderLink)}

          {user?.role === 'COLLECTOR' ? (
            <>
              <div className="nav-section">Collector</div>
              {renderLink({ to: '/collector', label: 'Collector workspace', icon: '🧰' })}
            </>
          ) : null}

          {user?.role === 'ADMIN' ? (
            <>
              <div className="nav-section">Administration</div>
              {renderLink({ to: '/admin', label: 'Admin console', icon: '🛠️' })}
            </>
          ) : null}

          <div className="nav-section">Account</div>
          {ACCOUNT_NAV.map(renderLink)}
        </nav>

        <div className="sidebar-footer">
          <div className="small muted" style={{ wordBreak: 'break-word' }}>
            {user?.email}
          </div>
          <div className="inline" style={{ marginTop: 8 }}>
            <span className="pill grey">{user?.role?.toLowerCase()}</span>
          </div>
          <button type="button" className="btn ghost small" style={{ marginTop: 12 }} onClick={onLogout}>
            Sign out
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div>
            <div className="topbar-title">{meta.title}</div>
            {meta.sub ? <div className="topbar-sub">{meta.sub}</div> : null}
          </div>
          <div className="spacer" />
          <NavLink to="/notifications" className="btn ghost small" aria-label="Notifications">
            🔔 {unread > 0 ? <span className="pill red">{unread}</span> : null}
          </NavLink>
        </header>
        <main className="page">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
