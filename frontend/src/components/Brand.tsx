import { Link } from 'react-router-dom';
import { Icon } from './icons';

/** The ReLoop wordmark. Used in the marketing nav, app bar and auth screens. */
export function Brand({ to = '/', tag = true, className = '' }: { to?: string; tag?: boolean; className?: string }) {
  return (
    <Link to={to} className={`brand ${className}`.trim()} aria-label="ReLoop home">
      <span className="brand-mark" aria-hidden="true">
        <Icon name="recycle" size={19} strokeWidth={1.7} />
      </span>
      <span>
        <span className="brand-name">ReLoop</span>
        {tag ? <span className="brand-tag">Circular waste</span> : null}
      </span>
    </Link>
  );
}
