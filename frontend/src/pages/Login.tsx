import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Field, Note, Spinner } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { AuthAside } from './AuthAside';

export function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation() as { state?: { from?: string } };
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [resetOpen, setResetOpen] = useState(false);
  const [resetEmail, setResetEmail] = useState('');
  const [resetMessage, setResetMessage] = useState<string | null>(null);
  const [devToken, setDevToken] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [resetDone, setResetDone] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate(location.state?.from ?? '/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not sign you in. Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const requestReset = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setResetMessage(null);
    setDevToken(null);
    setResetDone(false);
    try {
      const result = await api.forgotPassword(resetEmail);
      setResetMessage(result.message);
      setDevToken(result.devResetToken);
    } catch (err) {
      setResetMessage(err instanceof ApiError ? err.message : 'Could not start the reset process.');
    } finally {
      setBusy(false);
    }
  };

  const completeReset = async (event: FormEvent) => {
    event.preventDefault();
    if (!devToken) return;
    setBusy(true);
    try {
      await api.resetPassword(devToken, newPassword);
      setResetDone(true);
      setDevToken(null);
      setPassword(newPassword);
    } catch (err) {
      setResetMessage(err instanceof ApiError ? err.message : 'Could not reset the password.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-shell">
      <AuthAside />
      <div className="auth-form-side">
        <div className="auth-card">
          <h2>Welcome back</h2>
          <p className="muted small">Sign in to keep tracking your recycling.</p>

          {error ? <Note tone="error">{error}</Note> : null}

          <form onSubmit={submit} style={{ marginTop: 16 }}>
            <Field label="Email" htmlFor="email">
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
              />
            </Field>
            <Field label="Password" htmlFor="password">
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </Field>
            <Button type="submit" disabled={busy}>
              {busy ? <Spinner onPrimary /> : null}
              {busy ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <div className="switch">
            New to ReLoop? <Link to="/register">Create an account</Link>
          </div>
          <button type="button" className="btn ghost small" style={{ marginTop: 8 }} onClick={() => setResetOpen((v) => !v)}>
            {resetOpen ? 'Hide password reset' : 'Forgot your password?'}
          </button>

          {resetOpen ? (
            <div className="card tight" style={{ marginTop: 12 }}>
              <h3>Reset your password</h3>
              <form onSubmit={requestReset}>
                <Field label="Account email" htmlFor="reset-email">
                  <input
                    id="reset-email"
                    type="email"
                    required
                    value={resetEmail}
                    onChange={(e) => setResetEmail(e.target.value)}
                  />
                </Field>
                <Button type="submit" className="secondary small" disabled={busy}>
                  Send reset instructions
                </Button>
              </form>

              {resetMessage ? (
                <div style={{ marginTop: 12 }}>
                  <Note tone={resetDone ? 'success' : 'info'}>{resetMessage}</Note>
                </div>
              ) : null}

              {devToken ? (
                <form onSubmit={completeReset} style={{ marginTop: 12 }}>
                  <Note tone="warning">
                    Email delivery is not configured on this server, so the reset token is shown here for development. In
                    production this token would arrive by email.
                  </Note>
                  <div className="divider" />
                  <Field label="Reset token" htmlFor="reset-token">
                    <input id="reset-token" value={devToken} readOnly />
                  </Field>
                  <Field label="New password" htmlFor="reset-new" hint="At least 8 characters">
                    <input
                      id="reset-new"
                      type="password"
                      required
                      minLength={8}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                    />
                  </Field>
                  <Button type="submit" disabled={busy}>
                    Set new password
                  </Button>
                </form>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
