import { Card, EmptyState, Loading, Note, Stat, useAsync } from '../components/ui';
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
      <div className="page-head">
        <div>
          <h1>My impact</h1>
          <p className="lede">
            Every figure below is derived from the actual weight collectors recorded — nothing is extrapolated from
            estimates.
          </p>
        </div>
      </div>

      <div className="grid cols-3">
        <Stat label="Total recycled" value={`${Number(data.totalCollectedKg).toFixed(2)} kg`} accent hint="Actual weighed weight" />
        <Stat label="Completed pickups" value={data.completedPickups} hint="Closed the loop" />
        <Stat label="Est. CO₂e avoided" value={`${totalCo2e.toFixed(1)} kg`} hint="Approximate — see methodology" />
      </div>

      <Card title="Breakdown by material" className="">
        {!hasData ? (
          <EmptyState icon="🌍" title="No impact recorded yet">
            Book a pickup and once the collector records the collected weight, your impact will appear here.
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
                  <div className="list-meta">{Number(category.estimatedCo2eKgSaved).toFixed(2)} kg CO₂e</div>
                </span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card title="Methodology">
        <p className="small muted" style={{ margin: 0 }}>
          {data.methodology}
        </p>
        {data.estimatesAreApproximations ? (
          <div style={{ marginTop: 12 }}>
            <Note tone="info" icon="📐">
              Environmental benefit numbers are <strong>estimates</strong> based on conservative published lifecycle
              coefficients. They are labelled as approximations and should not be used for compliance reporting.
            </Note>
          </div>
        ) : null}
      </Card>
    </>
  );
}
