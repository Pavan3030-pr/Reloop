import { Link } from 'react-router-dom';
import { ReLoopMark } from './ReLoopMark';

/**
 * The ReLoop lockup: the recycling mark plus the wordmark. Used in the marketing nav, the app bar,
 * the auth screens and the footer, so the identity stays identical everywhere.
 */
export function Brand({ to = '/', tag = true, className = '' }: { to?: string; tag?: boolean; className?: string }) {
  return (
    <Link to={to} className={`brand ${className}`.trim()} aria-label="ReLoop home">
      <span className="brand-mark" aria-hidden="true">
        <ReLoopMark size={20} />
      </span>
      <span>
        <span className="brand-name">ReLoop</span>
        {tag ? <span className="brand-tag">Circular waste</span> : null}
      </span>
    </Link>
  );
}
