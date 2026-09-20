export function AuthAside() {
  const points = [
    'Scan an item and let AI suggest the right recycling stream.',
    'Find verified collection points near you, with distances.',
    'Book a pickup and follow it from acceptance to recovery.',
    'Track real kilograms collected — and the CO₂e they represent.',
  ];

  return (
    <div className="auth-art">
      <div className="brand" style={{ padding: 0, marginBottom: 22 }}>
        <span className="brand-mark" aria-hidden="true">
          ♻
        </span>
        <span>
          <span className="brand-name">ReLoop</span>
          <span className="brand-tag">Circular waste</span>
        </span>
      </div>

      <h1>Give every piece of waste a better destination.</h1>
      <p className="lede">
        ReLoop connects households, verified collectors and recycling facilities so materials stay in the loop instead of
        reaching a landfill.
      </p>

      <ul className="auth-points">
        {points.map((point) => (
          <li key={point}>
            <span className="tick" aria-hidden="true">
              ✓
            </span>
            <span>{point}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
