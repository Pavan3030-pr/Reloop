import { useCallback, useEffect, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import type { PickupStatus } from '../lib/types';
import { statusLabel, statusTone } from '../lib/format';

/**
 * Primary action button. Layout variants (secondary/ghost/danger/small) are passed
 * through the class list so pages can compose them freely.
 */
export function Button({
  children,
  className = '',
  variant,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' }) {
  const variantClass = variant && variant !== 'primary' ? variant : '';
  return (
    <button type="button" className={`btn ${variantClass} ${className}`.trim()} {...rest}>
      {children}
    </button>
  );
}

export function Spinner({ onPrimary = false }: { onPrimary?: boolean }) {
  return <span className={`spinner${onPrimary ? ' on-primary' : ''}`} aria-hidden="true" />;
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading-block" role="status">
      <Spinner />
      <span>{label}</span>
    </div>
  );
}

export function Card({
  children,
  className = '',
  title,
  action,
}: {
  children: ReactNode;
  className?: string;
  title?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <section className={`card ${className}`.trim()}>
      {(title || action) && (
        <div className="card-head">
          {title ? <h2>{title}</h2> : null}
          <div className="spacer" />
          {action}
        </div>
      )}
      {children}
    </section>
  );
}

export function Stat({
  label,
  value,
  hint,
  accent = false,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  accent?: boolean;
}) {
  return (
    <div className={`stat${accent ? ' accent' : ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value mono">{value}</div>
      {hint ? <div className="stat-hint">{hint}</div> : null}
    </div>
  );
}

export function Note({
  tone = 'info',
  children,
  icon,
}: {
  tone?: 'info' | 'error' | 'success' | 'warning';
  children: ReactNode;
  icon?: string;
}) {
  const fallback = { info: 'ℹ️', error: '⚠️', success: '✅', warning: '⚠️' }[tone];
  return (
    <div className={`note ${tone}`} role={tone === 'error' ? 'alert' : undefined}>
      <span aria-hidden="true">{icon ?? fallback}</span>
      <div>{children}</div>
    </div>
  );
}

export function EmptyState({
  icon = '🌱',
  title,
  children,
  action,
}: {
  icon?: string;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon" aria-hidden="true">
        {icon}
      </span>
      <div className="strong">{title}</div>
      {children ? <div className="small" style={{ marginTop: 6 }}>{children}</div> : null}
      {action ? <div style={{ marginTop: 14 }}>{action}</div> : null}
    </div>
  );
}

export function StatusPill({ status }: { status: PickupStatus | string | null | undefined }) {
  return (
    <span className={`pill ${statusTone(status)}`}>
      <span className="dot" aria-hidden="true" />
      {statusLabel(status)}
    </span>
  );
}

export function CategoryChip({ name, colorHex }: { name: string; colorHex?: string | null }) {
  return (
    <span className="chip">
      <span className="swatch" style={{ background: colorHex ?? '#6B705C' }} aria-hidden="true" />
      {name}
    </span>
  );
}

export function Field({
  label,
  hint,
  error,
  children,
  htmlFor,
}: {
  label: string;
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
  htmlFor?: string;
}) {
  return (
    <div className="field">
      <label htmlFor={htmlFor}>{label}</label>
      {children}
      {hint ? <span className="hint">{hint}</span> : null}
      {error ? <span className="error">{error}</span> : null}
    </div>
  );
}

interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
  setData: (value: T | null) => void;
}

/** Small data-fetching hook with loading/error state and manual reload. */
export function useAsync<T>(loader: () => Promise<T>, deps: unknown[] = []): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  const run = useCallback(loader, deps); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    run()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Something went wrong');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [run, nonce]);

  return { data, loading, error, reload: () => setNonce((n) => n + 1), setData };
}
