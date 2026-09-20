import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button, Field, Note, Spinner } from '../components/ui';
import { ApiError } from '../lib/api';
import { useAuth } from '../context/AuthContext';
import { AuthAside } from './AuthAside';

export function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await register({ email, password, fullName, phone: phone || undefined });
      navigate('/', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Could not create your account. Please try again.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-shell">
      <AuthAside />
      <div className="auth-form-side">
        <div className="auth-card">
          <h2>Create your ReLoop account</h2>
          <p className="muted small">It takes less than a minute.</p>

          {error ? <Note tone="error">{error}</Note> : null}

          <form onSubmit={submit} style={{ marginTop: 16 }}>
            <Field label="Full name" htmlFor="fullName" error={fieldErrors.fullName}>
              <input
                id="fullName"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Asha Sharma"
              />
            </Field>
            <Field label="Email" htmlFor="email" error={fieldErrors.email}>
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
            <Field label="Phone" htmlFor="phone" hint="Optional — collectors use it to confirm pickups" error={fieldErrors.phone}>
              <input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+91 90000 00000" />
            </Field>
            <Field label="Password" htmlFor="password" hint="At least 8 characters" error={fieldErrors.password}>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </Field>
            <Button type="submit" disabled={busy}>
              {busy ? <Spinner onPrimary /> : null}
              {busy ? 'Creating account…' : 'Create account'}
            </Button>
          </form>

          <div className="switch">
            Already registered? <Link to="/login">Sign in</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
