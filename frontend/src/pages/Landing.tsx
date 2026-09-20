import { Link } from 'react-router-dom';
import { Icon, type IconName } from '../components/icons';
import { MarketingShell } from '../components/MarketingShell';
import { useRevealOnScroll } from '../hooks/useReveal';
import { PICKUP_STAGES } from '../lib/format';

const PROBLEMS = [
  'What is this material, really?',
  'Can it be recycled at all — or is it hazardous?',
  'Where do I take it, and does it accept my material?',
  'Is a collection available in my area?',
  'What actually happened after it was picked up?',
];

const STEPS: { no: string; icon: IconName; title: string; body: string }[] = [
  { no: '01', icon: 'camera', title: 'Scan', body: 'Photograph the item, or add it manually if the picture is unclear.' },
  { no: '02', icon: 'sparkles', title: 'Understand', body: 'ReLoop identifies the material and returns disposal guidance you can act on.' },
  { no: '03', icon: 'pin', title: 'Connect', body: 'Find a verified collection point or request a pickup from a verified collector.' },
  { no: '04', icon: 'scale', title: 'Recover', body: 'Follow the pickup to processing, and see the weight that was really recovered.' },
];

const JOURNEY: { icon: IconName; label: string; sub: string; final?: boolean }[] = [
  { icon: 'box', label: 'Waste', sub: 'A household item' },
  { icon: 'truck', label: 'Collection', sub: 'Verified pickup' },
  { icon: 'filter', label: 'Sorting', sub: 'Separated by material' },
  { icon: 'factory', label: 'Processing', sub: 'Prepared for reuse' },
  { icon: 'recycle', label: 'Recovery', sub: 'Material reclaimed' },
  { icon: 'sprout', label: 'New life', sub: 'Feedstock, not landfill', final: true },
];

const TRACK_COPY: Record<string, string> = {
  REQUESTED: 'You book a slot and a verified collector sees the request.',
  ACCEPTED: 'A collector in your city takes responsibility for the job.',
  SCHEDULED: 'A visit time is agreed and shown on your pickup page.',
  PICKED_UP: 'The material is weighed on site — this is the weight that counts.',
  PROCESSING: 'It reaches the facility and moves into processing.',
  RECOVERED: 'Recovery is recorded and the loop is closed.',
};

const HOUSEHOLD_POINTS = [
  'Identify an item before you decide what to do with it',
  'See recycling guidance and hazardous-material warnings',
  'Find verified drop-off points, with distance when you share a location',
  'Request a pickup and follow every stage of it',
  'Watch your measured kilograms — and the estimates derived from them',
];

const COLLECTOR_POINTS = [
  'Receive pickup requests for the materials you accept',
  'Accept a job, schedule the visit and release it if plans change',
  'Record the actual weighed collection on site',
  'Move material through processing to recovery',
  'Build a verified collection history you can point to',
];

export function Landing() {
  const root = useRevealOnScroll([]);

  return (
    <MarketingShell>
      <div ref={root}>
        {/* ------------------------------------------------------------ hero */}
        <section className="hero">
          <div className="container hero-grid">
            <div className="hero-copy">
              <span className="eyebrow">Circular waste platform</span>
              <h1 className="display">
                Your waste has <span className="hl">another life</span>.
              </h1>
              <p className="lead">
                ReLoop uses AI to identify waste and connects you with the right collection and recovery pathway — then
                tracks the kilograms that were actually recovered.
              </p>
              <div className="cta-row">
                <Link className="btn lg" to="/register">
                  Scan your waste
                  <Icon name="arrowRight" size={18} />
                </Link>
                <a className="btn secondary lg" href="#how-it-works">
                  See how it works
                </a>
              </div>
              <div className="hero-foot">
                <span className="hf-item">
                  <Icon name="sparkles" size={16} />
                  AI identification
                </span>
                <span className="hf-item">
                  <Icon name="shield" size={16} />
                  Verified collectors
                </span>
                <span className="hf-item">
                  <Icon name="scale" size={16} />
                  Measured recovery
                </span>
              </div>
            </div>

            <div className="loop-card">
              <div className="loop-head">
                <span className="icon-tile" aria-hidden="true">
                  <Icon name="recycle" size={18} />
                </span>
                <span className="title">The ReLoop loop</span>
                <span className="spacer" />
                <span className="tag leaf">One material, end to end</span>
              </div>

              <div className="loop-rows">
                <span className="loop-rail" aria-hidden="true">
                  <span className="loop-dot" />
                </span>
                <div className="loop-row">
                  <span className="icon-tile" aria-hidden="true">
                    <Icon name="box" size={17} />
                  </span>
                  <div className="loop-body">
                    <div className="loop-label">Waste item</div>
                    <div className="loop-sub">Photograph it, or record it manually</div>
                  </div>
                </div>
                <div className="loop-row">
                  <span className="icon-tile" aria-hidden="true">
                    <Icon name="sparkles" size={17} />
                  </span>
                  <div className="loop-body">
                    <div className="loop-label">AI identification</div>
                    <div className="loop-sub">Material, category, recyclability, guidance</div>
                  </div>
                </div>
                <div className="loop-row">
                  <span className="icon-tile" aria-hidden="true">
                    <Icon name="truck" size={17} />
                  </span>
                  <div className="loop-body">
                    <div className="loop-label">Collection</div>
                    <div className="loop-sub">A verified point or a booked pickup</div>
                  </div>
                </div>
                <div className="loop-row">
                  <span className="icon-tile" aria-hidden="true">
                    <Icon name="factory" size={17} />
                  </span>
                  <div className="loop-body">
                    <div className="loop-label">Recovery</div>
                    <div className="loop-sub">Processing recorded, weight preserved</div>
                  </div>
                </div>
              </div>

              <div className="loop-tags">
                <span className="tag">Confidence</span>
                <span className="tag">Recyclable</span>
                <span className="tag">Hazardous</span>
                <span className="tag">Disposal guidance</span>
              </div>
            </div>
          </div>
        </section>

        {/* --------------------------------------------------------- problem */}
        <section className="section rule">
          <div className="container problem-grid">
            <div data-reveal>
              <span className="eyebrow">The gap</span>
              <h2 className="h2">
                Most waste is thrown away on a guess.
              </h2>
              <p className="lead">
                Recycling fails long before the material reaches a facility. It fails at the moment of decision — when
                nobody can answer a simple question about the thing in your hand.
              </p>
            </div>
            <ul className="problem-list" data-reveal>
              {PROBLEMS.map((question, index) => (
                <li key={question}>
                  <span className="idx">{String(index + 1).padStart(2, '0')}</span>
                  <span className="q">{question}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* --------------------------------------------------- how it works */}
        <section className="section wash" id="how-it-works">
          <div className="container">
            <div style={{ maxWidth: '62ch' }} data-reveal>
              <span className="eyebrow">How ReLoop works</span>
              <h2 className="h2">Four steps from uncertain to recovered.</h2>
              <p className="lead">
                Nothing is estimated into existence: the figures you see come from what a collector recorded on site.
              </p>
            </div>

            <div className="steps" data-reveal>
              {STEPS.map((step) => (
                <div className="step" key={step.no}>
                  <span className="step-no">{step.no}</span>
                  <span className="icon-tile lg" aria-hidden="true">
                    <Icon name={step.icon} size={20} />
                  </span>
                  <div className="step-title">{step.title}</div>
                  <p>{step.body}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------- scanner */}
        <section className="section">
          <div className="container split-wide">
            <div data-reveal>
              <span className="eyebrow">AI waste identification</span>
              <h2 className="h2">Ask before you bin.</h2>
              <p className="lead">
                Upload a photo and ReLoop returns a structured reading: the likely item, its category, a confidence
                score, whether it is recyclable or hazardous, and how to prepare it.
              </p>
              <ul className="feature-list">
                <li>
                  <span className="ftick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  Low-confidence results are flagged instead of presented as fact.
                </li>
                <li>
                  <span className="ftick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  You can always classify an item yourself — manual scans are recorded as manual.
                </li>
                <li>
                  <span className="ftick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  Every scan keeps its source, so AI-assisted and manual records never blur together.
                </li>
              </ul>
            </div>

            <div data-reveal>
              <div className="panel soft">
                <div className="card-head">
                  <span className="icon-tile" aria-hidden="true">
                    <Icon name="camera" size={18} />
                  </span>
                  <h3>Scanner</h3>
                  <span className="spacer" />
                  <span className="pill blue">
                    <span className="dot" />
                    Analysis
                  </span>
                </div>

                <div className="scan-stage" style={{ marginBottom: 16 }}>
                  <span className="scan-sweep" aria-hidden="true" />
                  <div style={{ textAlign: 'center', color: 'var(--muted)', padding: 24 }} className="small">
                    <Icon name="image" size={26} />
                    <div style={{ marginTop: 10 }}>Waiting for a photo of the item</div>
                  </div>
                </div>

                <dl className="readout">
                  <div className="readout-row">
                    <dt>Identified item</dt>
                    <dd>PET drinks bottle</dd>
                  </div>
                  <div className="readout-row">
                    <dt>Category</dt>
                    <dd>Plastic</dd>
                  </div>
                  <div className="readout-row">
                    <dt>Confidence</dt>
                    <dd className="mono">86%</dd>
                  </div>
                  <div className="readout-row">
                    <dt>Recyclable</dt>
                    <dd>Yes</dd>
                  </div>
                  <div className="readout-row">
                    <dt>Hazardous</dt>
                    <dd>No</dd>
                  </div>
                  <div className="readout-row">
                    <dt>Guidance</dt>
                    <dd>Empty, rinse and keep the cap separate</dd>
                  </div>
                </dl>

                <p className="caption">
                  Illustrative example of the scanner output. Live results depend on your photo and on the AI provider
                  configured for this deployment — ReLoop never invents a reading when the provider is unavailable.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------- collection */}
        <section className="section rule">
          <div className="container split-wide">
            <div data-reveal>
              <span className="eyebrow">Collection</span>
              <h2 className="h2">Somewhere real accepts it.</h2>
              <p className="lead">
                ReLoop keeps a directory of collection points with the materials each one accepts, who verified it and
                how far away it is from you.
              </p>
              <ul className="feature-list">
                <li>
                  <span className="ftick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  Filter by material, city or keyword — and by radius when you share a location.
                </li>
                <li>
                  <span className="ftick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  Verified and unverified records are labelled differently, never merged.
                </li>
                <li>
                  <span className="ftick" aria-hidden="true">
                    <Icon name="check" size={13} />
                  </span>
                  No drop-off nearby? Request a pickup instead and a collector comes to you.
                </li>
              </ul>
            </div>

            <div data-reveal>
              <div className="panel" style={{ padding: 0, overflow: 'hidden' }}>
                <div className="map-panel" role="img" aria-label="Illustration of collection points plotted by distance">
                  <span className="map-pin" style={{ left: '26%', top: '40%' }}>
                    <span className="pin-dot" aria-hidden="true">
                      <Icon name="recycle" size={13} />
                    </span>
                    <span className="pin-label">Verified point</span>
                  </span>
                  <span className="map-pin" style={{ left: '68%', top: '30%' }}>
                    <span className="pin-dot" aria-hidden="true">
                      <Icon name="recycle" size={13} />
                    </span>
                    <span className="pin-label">Verified point</span>
                  </span>
                  <span className="map-pin" style={{ left: '52%', top: '74%' }}>
                    <span className="pin-dot" aria-hidden="true">
                      <Icon name="recycle" size={13} />
                    </span>
                    <span className="pin-label">Verified point</span>
                  </span>
                  <span className="map-me" style={{ left: '44%', top: '56%' }} />
                  <span className="map-legend">Distance from your location · illustrative layout</span>
                </div>

                <div className="list" style={{ padding: '4px 18px 12px' }}>
                  {[
                    { name: 'Household recyclables', meta: 'Accepts plastics, paper, metal, glass' },
                    { name: 'Dry waste transfer point', meta: 'Accepts plastics, e-waste' },
                    { name: 'Neighbourhood drop-off', meta: 'Accepts glass, paper' },
                  ].map((point) => (
                    <div className="list-item" key={point.name}>
                      <span className="icon-tile" aria-hidden="true">
                        <Icon name="pin" size={17} />
                      </span>
                      <div className="grow">
                        <div className="list-title">{point.name}</div>
                        <div className="list-meta">{point.meta}</div>
                      </div>
                      <span className="pill green">
                        <Icon name="shield" size={12} />
                        Verified
                      </span>
                    </div>
                  ))}
                </div>
              </div>
              <p className="caption">
                Illustrative list. In the app these rows are real directory records with their own materials, hours and
                distance.
              </p>
            </div>
          </div>
        </section>

        {/* ----------------------------------------------------------- pickup */}
        <section className="section wash">
          <div className="container">
            <div style={{ maxWidth: '62ch' }} data-reveal>
              <span className="eyebrow">Pickup tracking</span>
              <h2 className="h2">Six stages, one honest record.</h2>
              <p className="lead">
                Every pickup carries a real reference and a timeline. No stage is shown as complete before the system
                recorded it.
              </p>
            </div>

            <div className="track" data-reveal>
              {PICKUP_STAGES.map((stage) => (
                <div className="track-step done" key={stage.key}>
                  <span className="tdot" aria-hidden="true">
                    <Icon name="check" size={12} />
                  </span>
                  <div className="tlabel">{stage.label}</div>
                  <div className="tdesc">{TRACK_COPY[stage.key]}</div>
                </div>
              ))}
            </div>

            <p className="caption" data-reveal>
              The stages shown are the ones the platform actually enforces, including invalid-transition protection —
              a pickup cannot jump from requested to recovered.
            </p>
          </div>
        </section>

        {/* -------------------------------------------------------- circular */}
        <section className="section">
          <div className="container">
            <div style={{ maxWidth: '62ch' }} data-reveal>
              <span className="eyebrow">The circular journey</span>
              <h2 className="h2">A material path, not a bin.</h2>
              <p className="lead">
                ReLoop’s whole purpose is to keep material moving along this line, and to be honest about where each
                item actually got to.
              </p>
            </div>

            <div className="journey" data-reveal>
              {JOURNEY.map((node) => (
                <div className={`journey-node${node.final ? ' final' : ''}`} key={node.label}>
                  <span className="jn-icon" aria-hidden="true">
                    <Icon name={node.icon} size={20} />
                  </span>
                  <div className="jn-label">{node.label}</div>
                  <div className="jn-sub">{node.sub}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------- audiences */}
        <section className="section rule">
          <div className="container">
            <div className="two-col">
              <div className="audience" id="households" data-reveal>
                <span className="eyebrow">For households</span>
                <div className="audience-title">Know what you are holding.</div>
                <p className="muted small" style={{ marginTop: 10 }}>
                  Start with a photo, end with a recovered material record.
                </p>
                <ul className="feature-list">
                  {HOUSEHOLD_POINTS.map((point) => (
                    <li key={point}>
                      <span className="ftick" aria-hidden="true">
                        <Icon name="check" size={13} />
                      </span>
                      {point}
                    </li>
                  ))}
                </ul>
              </div>

              <div className="audience dark" id="collectors" data-reveal>
                <span className="eyebrow" style={{ color: 'rgba(255,255,255,0.7)' }}>
                  For collectors
                </span>
                <div className="audience-title">Run the work, not a spreadsheet.</div>
                <p className="small" style={{ marginTop: 10, color: 'rgba(255,255,255,0.72)' }}>
                  A workspace built around the collection itself: accept, schedule, weigh, close.
                </p>
                <ul className="feature-list">
                  {COLLECTOR_POINTS.map((point) => (
                    <li key={point}>
                      <span className="ftick" aria-hidden="true">
                        <Icon name="check" size={13} />
                      </span>
                      {point}
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------- impact */}
        <section className="section wash" id="impact">
          <div className="container">
            <div style={{ maxWidth: '66ch' }} data-reveal>
              <span className="eyebrow">Impact</span>
              <h2 className="h2">We only report what was measured.</h2>
              <p className="lead">
                ReLoop tracks four things per account. Until a collector records a real collection, the honest answer is
                that there is nothing to show yet — so no figures are invented for this page.
              </p>
            </div>

            <div className="metrics" data-reveal>
              <div className="metric measured">
                <div className="m-label">Waste collected</div>
                <div className="m-value mono">—</div>
                <div className="m-note">Kilograms a collector weighed on site</div>
              </div>
              <div className="metric measured">
                <div className="m-label">Material recovered</div>
                <div className="m-value mono">—</div>
                <div className="m-note">Collected items taken through to recovery</div>
              </div>
              <div className="metric">
                <div className="m-label">Collection categories</div>
                <div className="m-value mono">—</div>
                <div className="m-note">Which material streams your waste falls into</div>
              </div>
              <div className="metric">
                <div className="m-label">Recovery history</div>
                <div className="m-value mono">—</div>
                <div className="m-note">A dated record of every completed collection</div>
              </div>
            </div>

            <div className="two-col" data-reveal>
              <div className="audience">
                <span className="icon-tile lg" aria-hidden="true">
                  <Icon name="scale" size={20} />
                </span>
                <div className="audience-title" style={{ marginTop: 14 }}>
                  Measured data
                </div>
                <p className="muted small" style={{ margin: 0 }}>
                  Totals, material breakdowns and history are sums of recorded weights. They are facts about your
                  account and are never smoothed or extrapolated.
                </p>
              </div>
              <div className="audience">
                <span className="icon-tile lg" aria-hidden="true">
                  <Icon name="trending" size={20} />
                </span>
                <div className="audience-title" style={{ marginTop: 14 }}>
                  Estimated environmental impact
                </div>
                <p className="muted small" style={{ margin: 0 }}>
                  CO₂e avoided is an <strong>estimate</strong> derived from conservative published lifecycle
                  coefficients per material. It is labelled as an approximation everywhere it appears and is not a
                  compliance figure.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* -------------------------------------------------------- final CTA */}
        <section className="final-cta">
          <div className="container" data-reveal>
            <h2 className="display">Give every piece of waste a better destination.</h2>
            <p className="lead" style={{ marginInline: 'auto', marginBottom: 28 }}>
              Create an account, scan your first item, and see where it can actually go.
            </p>
            <div className="cta-row" style={{ justifyContent: 'center' }}>
              <Link className="btn lg" to="/register">
                Start recycling
                <Icon name="arrowRight" size={18} />
              </Link>
              <Link className="btn secondary lg" to="/login">
                I already have an account
              </Link>
            </div>
          </div>
        </section>
      </div>
    </MarketingShell>
  );
}
