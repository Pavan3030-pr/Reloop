import { MarketingShell } from '../components/MarketingShell';
import { useRevealOnScroll } from '../hooks/useReveal';

const PRIVACY: { heading: string; body: string }[] = [
  {
    heading: 'What ReLoop stores',
    body: 'Your account email, a hashed password, and the profile details you choose to add (name, phone, city, address). Scans you save, pickups you request, their status history, and notifications are linked to your account.',
  },
  {
    heading: 'Photos and AI analysis',
    body: 'When you use the waste scanner, the image you upload is sent to the AI provider configured for this deployment so it can identify the material. Images are stored on this server when you attach them to a scan or a pickup. If no provider is configured, no image is analysed and the scan stays manual.',
  },
  {
    heading: 'Location',
    body: 'Your browser location is only read when you explicitly press a location button, and only to sort collection points by distance or to attach coordinates to a pickup. Declining leaves everything else working.',
  },
  {
    heading: 'Session tokens',
    body: 'Signing in stores a short-lived access token and a refresh token in your browser’s local storage. Signing out clears them locally and revokes the refresh token on the server.',
  },
  {
    heading: 'What ReLoop does not do',
    body: 'There is no third-party analytics or advertising code in this client, and personal data is not sold or shared beyond the services required to run the platform (the database, the file storage and the AI provider used for scanning).',
  },
];

const TERMS: { heading: string; body: string }[] = [
  {
    heading: 'Using the platform',
    body: 'ReLoop is a software platform for identifying waste, finding collection options and recording collections. You are responsible for what you submit: the accuracy of your descriptions, the address you give, and the state of the material you hand over.',
  },
  {
    heading: 'Collectors',
    body: 'Collections are performed by independent collection organisations. Each one is reviewed by an administrator before it can accept jobs, but ReLoop does not guarantee any individual organisation’s availability, pricing or outcomes.',
  },
  {
    heading: 'Recorded weights and estimates',
    body: 'Collection weights are recorded by the collector performing the collection. Environmental figures such as CO₂e avoided are approximations derived from published lifecycle coefficients, are labelled as estimates, and must not be used for compliance or offset reporting.',
  },
  {
    heading: 'Hazardous material',
    body: 'Hazardous items must be handled according to local rules. ReLoop flags hazardous material but does not license you to transport it, and does not accept hazardous waste through a standard household pickup.',
  },
  {
    heading: 'Availability',
    body: 'This deployment is provided as-is. Features that depend on external services — in particular AI identification — may be unavailable, and the platform is designed to tell you so rather than fabricate a result.',
  },
];

export function Legal({ kind }: { kind: 'privacy' | 'terms' }) {
  const root = useRevealOnScroll([]);
  const isPrivacy = kind === 'privacy';
  const sections = isPrivacy ? PRIVACY : TERMS;

  return (
    <MarketingShell>
      <div ref={root}>
        <section className="section">
          <div className="container" style={{ maxWidth: 760 }} data-reveal>
            <span className="eyebrow">{isPrivacy ? 'Privacy' : 'Terms'}</span>
            <h1 className="h2">{isPrivacy ? 'How ReLoop handles your data' : 'Terms of use'}</h1>
            <p className="lead">
              {isPrivacy
                ? 'A plain description of what this platform stores and why — written to match what the software actually does.'
                : 'Plain terms for using this deployment of ReLoop.'}
            </p>

            <div className="stack" style={{ marginTop: 34 }}>
              {sections.map((section) => (
                <div className="card" key={section.heading}>
                  <h2 style={{ fontSize: '1.05rem', marginBottom: 8 }}>{section.heading}</h2>
                  <p className="muted small" style={{ margin: 0 }}>
                    {section.body}
                  </p>
                </div>
              ))}
            </div>

            <p className="caption" style={{ marginTop: 26 }}>
              Last updated {new Date().toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}.
            </p>
          </div>
        </section>
      </div>
    </MarketingShell>
  );
}
