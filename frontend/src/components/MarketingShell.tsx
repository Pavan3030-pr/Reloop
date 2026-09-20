import { useCallback, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Brand } from './Brand';
import { Icon } from './icons';
import { useAuth } from '../context/AuthContext';
import { useScrollLock, useScrolled } from '../hooks/useReveal';

const SECTIONS = [
  { id: 'how-it-works', label: 'How it works' },
  { id: 'households', label: 'For households' },
  { id: 'collectors', label: 'For collectors' },
  { id: 'impact', label: 'Impact' },
];

/** Marketing shell nav — anchor links scroll in-page, and deep-link back to the
 * landing page when the visitor is on a legal route. */
export function MarketingNav() {
  const navigate = useNavigate();
  const stuck = useScrolled(10);
  const [open, setOpen] = useState(false);
  const { user, loading } = useAuth();
  const appHref = user ? '/dashboard' : '/register';

  useScrollLock(open);

  const goTo = useCallback(
    (id: string) => {
      setOpen(false);
      const scroll = () => {
        const target = document.getElementById(id);
        if (!target) return;
        const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        target.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
      };
      if (window.location.pathname !== '/') {
        navigate('/');
        window.setTimeout(scroll, 90);
      } else {
        scroll();
      }
    },
    [navigate],
  );

  return (
    <>
      <header className={`mnav${stuck ? ' stuck' : ''}`}>
        <div className="mnav-inner">
          <Brand />

          <nav className="mnav-links" aria-label="Sections">
            {SECTIONS.map((section) => (
              <button key={section.id} type="button" onClick={() => goTo(section.id)}>
                {section.label}
              </button>
            ))}
          </nav>

          <div className="mnav-actions">
            {!loading && user ? (
              <Link className="btn small" to={appHref}>
                Open app
                <Icon name="arrowRight" size={16} />
              </Link>
            ) : (
              <>
                <Link className="btn ghost" to="/login">
                  Sign in
                </Link>
                <Link className="btn" to="/register">
                  Start recycling
                </Link>
              </>
            )}
            <button
              type="button"
              className="icon-btn bordered mnav-toggle"
              aria-label={open ? 'Close menu' : 'Open menu'}
              aria-expanded={open}
              onClick={() => setOpen((value) => !value)}
            >
              <Icon name={open ? 'close' : 'menu'} size={20} />
            </button>
          </div>
        </div>
      </header>

      {open ? (
        <div className="msheet" role="dialog" aria-label="Menu">
          <nav>
            {SECTIONS.map((section) => (
              <button key={section.id} type="button" onClick={() => goTo(section.id)}>
                {section.label}
              </button>
            ))}
          </nav>
          {user ? (
            <Link className="btn block" to={appHref}>
              Open app
            </Link>
          ) : (
            <>
              <Link className="btn secondary block" to="/login">
                Sign in
              </Link>
              <Link className="btn" to="/register">
                Start recycling
              </Link>
            </>
          )}
        </div>
      ) : null}
    </>
  );
}

export function MarketingFooter() {
  const navigate = useNavigate();

  const goTo = (id: string) => {
    const scroll = () => {
      const target = document.getElementById(id);
      if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };
    if (window.location.pathname !== '/') {
      navigate('/');
      window.setTimeout(scroll, 90);
    } else {
      scroll();
    }
  };

  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-grid">
          <div>
            <Brand />
            <p className="muted small" style={{ marginTop: 14, maxWidth: '32ch' }}>
              Give every piece of waste a better destination.
            </p>
          </div>

          <div>
            <h4>Platform</h4>
            <ul>
              {SECTIONS.map((section) => (
                <li key={section.id}>
                  <button type="button" onClick={() => goTo(section.id)}>
                    {section.label}
                  </button>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4>Account</h4>
            <ul>
              <li>
                <Link to="/login">Sign in</Link>
              </li>
              <li>
                <Link to="/register">Create an account</Link>
              </li>
              <li>
                <Link to="/dashboard">Open my dashboard</Link>
              </li>
            </ul>
          </div>

          <div>
            <h4>Details</h4>
            <ul>
              <li>
                <Link to="/privacy">Privacy</Link>
              </li>
              <li>
                <Link to="/terms">Terms</Link>
              </li>
              <li>
                <button type="button" onClick={() => goTo('impact')}>
                  Impact methodology
                </button>
              </li>
            </ul>
          </div>
        </div>

        <div className="footer-bottom">
          <span>© {new Date().getFullYear()} ReLoop · Give every piece of waste a better destination.</span>
          <span>
            Impact figures come from collector-recorded weights; environmental estimates are approximations.
          </span>
        </div>
      </div>
    </footer>
  );
}

export function MarketingShell({ children }: { children: ReactNode }) {
  return (
    <div>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <MarketingNav />
      <main id="main">{children}</main>
      <MarketingFooter />
    </div>
  );
}
