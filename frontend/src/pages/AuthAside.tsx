import { Brand } from '../components/Brand';
import { Icon } from '../components/icons';

const POINTS = [
  'Scan an item and let AI suggest the right recycling stream.',
  'Find verified collection points near you, with distances.',
  'Book a pickup and follow it from acceptance to recovery.',
  'Track real kilograms collected — and the CO₂e they represent.',
];

export function AuthAside() {
  return (
    <div className="auth-art">
      <div className="wrap-inner">
        <Brand />
        <h1>Give every piece of waste a better destination.</h1>
        <p className="lede">
          ReLoop connects households, verified collectors and recycling facilities so materials stay in the loop instead
          of reaching a landfill.
        </p>

        <ul className="auth-points">
          {POINTS.map((point) => (
            <li key={point}>
              <span className="tick" aria-hidden="true">
                <Icon name="check" size={13} />
              </span>
              <span>{point}</span>
            </li>
          ))}
        </ul>

        <p className="caption" style={{ marginTop: 30, maxWidth: '44ch' }}>
          Impact figures are derived from collector-recorded weights. Environmental benefit is always labelled as an
          estimate.
        </p>
      </div>
    </div>
  );
}
