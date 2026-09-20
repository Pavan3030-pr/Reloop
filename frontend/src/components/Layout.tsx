import { useCallback, useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { Brand } from './Brand';
import { Icon, type IconName } from './icons';
import { useDismiss, useScrollLock, useScrolled } from '../hooks/useReveal';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  end?: boolean;
}

const PRIMARY_NAV: NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', icon: 'home' },
  { to: '/scan', label: 'Scan waste', icon: 'camera' },
  { to: '/pickups', label: 'Pickups', icon: 'truck' },
  { to: '/points', label: 'Collection points', icon: 'pin' },
  { to: '/history', label: 'History', icon: 'archive' },
  { to: '/impact', label: 'Impact', icon: 'globe' },
];

/** Route → document title, so browser tabs and history entries read well. */
const TITLES: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/scan': 'Scan waste',
  '/points': 'Collection points',
  '/pickups': 'Pickups',
  '/history': 'Recycling history',
  '/impact': 'My impact',
  '/notifications': 'Notifications',
  '/profile': 'Profile',
  '/collector': 'Collector workspace',
  '/admin': 'Admin console',
};

export function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);
  const [firstName, setFirstName] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const stuck = useScrolled(6);
  const closeMenu = useCallback(() => setMenuOpen(false), []);
  const menuRef = useDismiss(menuOpen, closeMenu);

  useScrollLock(sheetOpen);

  useEffect(() => {
    const key = Object.keys(TITLES).find(
      (path) => location.pathname === path || location.pathname.startsWith(`${path}/`),
    );
    document.title = key ? `${TITLES[key]} · ReLoop` : 'ReLoop — Give every piece of waste a better destination';
  }, [location.pathname]);

  useEffect(() => {
    setMenuOpen(false);
    setSheetOpen(false);
  }, [location.pathname]);

  // Greet people by name where we have one; the menu still shows the full address.
  useEffect(() => {
    if (!user) return;
    let active = true;
    api
      .profile()
      .then((result) => {
        if (active) setFirstName((result.fullName ?? '').trim().split(/\s+/)[0] || null);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [user]);

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

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const secondaryNav: NavItem[] = [
    ...(user?.role === 'COLLECTOR' ? [{ to: '/collector', label: 'Collector workspace', icon: 'box' as IconName }] : []),
    ...(user?.role === 'ADMIN' ? [{ to: '/admin', label: 'Admin console', icon: 'settings' as IconName }] : []),
  ];

  const accountNav: NavItem[] = [
    { to: '/notifications', label: 'Notifications', icon: 'bell' },
    { to: '/profile', label: 'Profile', icon: 'user' },
  ];

  const renderLink = (item: NavItem) => (
    <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
      <span className="nav-icon" aria-hidden="true">
        <Icon name={item.icon} size={17} />
      </span>
      <span className="nav-label">{item.label}</span>
      {item.to === '/notifications' && unread > 0 ? <span className="nav-count">{unread}</span> : null}
    </NavLink>
  );

  const initials = (user?.email ?? '?').slice(0, 1).toUpperCase();

  return (
    <div className="app">
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <header className={`appbar${stuck ? ' stuck' : ''}`}>
        <div className="appbar-inner">
          <Brand to="/dashboard" />

          <nav className="appnav" aria-label="Primary">
            {PRIMARY_NAV.map(renderLink)}
            {secondaryNav.map(renderLink)}
          </nav>

          <div className="appbar-actions">
            <Link to="/scan" className="icon-btn only-mobile" aria-label="Scan waste">
              <Icon name="camera" size={19} />
            </Link>

            <Link to="/notifications" className="icon-btn" aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}>
              <Icon name="bell" size={19} />
              {unread > 0 ? <span className="count">{unread > 9 ? '9+' : unread}</span> : null}
            </Link>

            <div className="user-menu" ref={menuRef}>
              <button
                type="button"
                className="user-trigger"
                onClick={() => setMenuOpen((open) => !open)}
                aria-expanded={menuOpen}
                aria-haspopup="menu"
              >
                <span className="avatar" aria-hidden="true">
                  {initials}
                </span>
                <span className="user-name" title={user?.email}>
                  {firstName ?? user?.email}
                </span>
                <Icon name="chevronDown" size={15} />
              </button>

              {menuOpen ? (
                <div className="menu" role="menu">
                  <div className="menu-head">
                    <div className="email">{user?.email}</div>
                    <div style={{ marginTop: 8 }}>
                      <span className="pill grey">{user?.role?.toLowerCase()}</span>
                    </div>
                  </div>
                  {accountNav.map((item) => (
                    <Link key={item.to} to={item.to} className="menu-item" role="menuitem">
                      <Icon name={item.icon} size={17} />
                      {item.label}
                    </Link>
                  ))}
                  {secondaryNav.map((item) => (
                    <Link key={item.to} to={item.to} className="menu-item" role="menuitem">
                      <Icon name={item.icon} size={17} />
                      {item.label}
                    </Link>
                  ))}
                  <div className="divider" style={{ margin: '6px 0' }} />
                  <button type="button" className="menu-item" role="menuitem" onClick={onLogout}>
                    <Icon name="logout" size={17} />
                    Sign out
                  </button>
                </div>
              ) : null}
            </div>

            <button
              type="button"
              className="icon-btn only-mobile"
              aria-label={sheetOpen ? 'Close navigation' : 'Open navigation'}
              aria-expanded={sheetOpen}
              onClick={() => setSheetOpen((open) => !open)}
            >
              <Icon name={sheetOpen ? 'close' : 'menu'} size={20} />
            </button>
          </div>
        </div>
      </header>

      {sheetOpen ? (
        <div className="sheet" role="dialog" aria-label="Navigation">
          <nav aria-label="Mobile">
            <div className="nav-section">Recycle</div>
            {PRIMARY_NAV.map(renderLink)}
            {secondaryNav.length > 0 ? <div className="nav-section">Workspace</div> : null}
            {secondaryNav.map(renderLink)}
            <div className="nav-section">Account</div>
            {accountNav.map(renderLink)}
          </nav>
          <button type="button" className="btn secondary block" style={{ marginTop: 22 }} onClick={onLogout}>
            <Icon name="logout" size={17} />
            Sign out
          </button>
        </div>
      ) : null}

      <main className="page" id="main">
        <Outlet />
      </main>
    </div>
  );
}
