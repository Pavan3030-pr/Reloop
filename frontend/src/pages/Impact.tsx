import { Card, EmptyState, Loading, Note, PageHead, Stat, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api } from '../lib/api';

export function Impact() {
  const impact = useAsync(() => api.impact(), []);

  if (impact.loading) return <Loading label="Calculating your impact…" />;
  if (impact.error) return <Note tone="error">{impact.error}</Note>;

  const data = impact.data;
  if (!data) return null;

  const totalCo2e = data.byCategory.reduce((sum, category) => sum + Number(category.estimatedCo2eKgSaved), 0);
  const maxKg = Math.max(1, ...data.byCategory.map((category) => Number(category.kg)));
  const hasData = Number(data.totalCollectedKg) > 0;

  return (
    <>
      <PageHead
        eyebrow="Measured"
        title="My impact"
        lede="Every kilogram below comes from the weight a collector recorded on site. Nothing here is extrapolated from your estimates, and environmental figures are labelled as approximations."
      />

      <div className="grid cols-3">
        <Stat
          label="Recycled, weighed on site"
          value={`${Number(data.totalCollectedKg).toFixed(2)} kg`}
          accent
          icon="scale"
          hint="Measured — from completed collections"
        />
        <Stat label="Completed collections" value={data.completedPickups} icon="checkCircle" hint="Loops fully closed" />
        <Stat
          label="Est. CO₂e avoided"
          value={`${totalCo2e.toFixed(1)} kg`}
          icon="trending"
          hint="Estimate — see methodology below"
        />
      </div>

      <Card title="Breakdown by material">
        {!hasData ? (
          <EmptyState icon={<Icon name="globe" size={20} />} title="No impact recorded yet">
            Book a pickup and once the collector records the collected weight, your impact will appear here with the
            real kilograms and the material split.
          </EmptyState>
        ) : (
          <div>
            {data.byCategory.map((category) => (
              <div className="meter-row" key={category.code}>
                <span className="inline">
                  <span className="swatch" style={{ background: category.colorHex ?? '#6B705C' }} aria-hidden="true" />
                  {category.name}
                </span>
                <span className="bar" aria-hidden="true">
                  <span style={{ width: `${(Number(category.kg) / maxKg) * 100}%` }} />
                </span>
                <span className="mono right">
                  {Number(category.kg).toFixed(2)} kg
                  <div className="list-meta">{Number(category.estimatedCo2eKgSaved).toFixed(2)} kg CO₂e (est.)</div>
                </span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <div className="grid cols-2" style={{ marginTop: 18 }}>
        <Card title="Measured data">
          <div className="inline" style={{ gap: 10, marginBottom: 12 }}>
            <span className="icon-tile" aria-hidden="true">
              <Icon name="scale" size={17} />
            </span>
            <span className="strong">What actually happened</span>
          </div>
          <p className="muted small" style={{ margin: 0 }}>
            Totals, the material breakdown and your recycling history are sums of collector-recorded weights. They are
            facts about your account, so they never change when estimates are revised.
          </p>
        </Card>

        <Card title="Estimated environmental impact">
          <div className="inline" style={{ gap: 10, marginBottom: 12 }}>
            <span className="icon-tile" aria-hidden="true">
              <Icon name="trending" size={17} />
            </span>
            <span className="strong">What it is likely worth</span>
          </div>
          <p className="muted small" style={{ margin: 0 }}>
            CO₂e avoided applies conservative published lifecycle coefficients to each material’s measured weight. It is
            an approximation for orientation only — not a compliance or offset figure.
          </p>
        </Card>
      </div>

      <Card title="Methodology">
        <p className="small muted" style={{ margin: 0 }}>
          {data.methodology}
        </p>
        {data.estimatesAreApproximations ? (
          <div style={{ marginTop: 14 }}>
            <Note tone="info">
              Environmental benefit numbers are <strong>estimates</strong> based on conservative published lifecycle
              coefficients. They are labelled as approximations everywhere they appear and should not be used for
              compliance reporting.
            </Note>
          </div>
        ) : null}
      </Card>
    </>
  );
}
