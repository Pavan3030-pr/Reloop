/**
 * The ReLoop mark: three arrows chasing each other around a circle.
 *
 * It is the recycling symbol reduced to clean geometry — one arrow unit, rotated three times, so
 * the shape reads as closed-loop recovery rather than as a generic "refresh" or "leaf" glyph. It is
 * drawn with `currentColor` so it inherits the brand green in the nav and app bar, and can be
 * inverted on dark surfaces.
 *
 * Exported as plain geometry so the same mark can be reused in the favicon (see
 * `public/favicon.svg`, which is the identical path data) and in larger illustrations where it
 * needs to spin.
 */
export function ReLoopMark({ size = 20, className = '' }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <g
        fill="none"
        stroke="currentColor"
        strokeWidth={1.85}
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {/* One arrow: a 120° arc of a circle centred at 12,12 with r=7, plus a head at its tip. */}
        {[0, 120, 240].map((rotation) => (
          <g key={rotation} transform={`rotate(${rotation} 12 12)`}>
            <path d="M12 5 A7 7 0 0 1 18.06 15.5" />
            <path d="M16.67 14.7 L19.45 16.3 L16.96 17.4" />
          </g>
        ))}
      </g>
    </svg>
  );
}
