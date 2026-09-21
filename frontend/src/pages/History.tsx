import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Loading, Note, PageHead, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api } from '../lib/api';
import { dateOnly, dateTime, kg } from '../lib/format';

export function History() {
  const categories = useAsync(() => api.categories(), []);
  const [material, setMaterial] = useState('');
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<{ material?: string; status?: string; from?: string; to?: string }>({});

  const history = useAsync(
    () => api.history({ ...filters, page, size: 10 }),
    [filters.material, filters.status, filters.from, filters.to, page],
  );

  const apply = () => {
    setPage(0);
    setFilters({ material: material || undefined, status: status || undefined, from: from || undefined, to: to || undefined });
  };

  const clear = () => {
    setMaterial('');
    setStatus('');
    setFrom('');
    setTo('');
    setPage(0);
    setFilters({});
  };

  const entries = history.data?.entries;
  const filtered = Boolean(filters.material || filters.status || filters.from || filters.to);

  return (
    <>
      <PageHead
        eyebrow="Measured record"
        title="Recycling history"
        lede="Only waste that a collector actually weighed and recorded appears here. These weights are what your impact figures are built from."
        actions={
          <Link className="btn secondary" to="/impact">
            <Icon name="globe" size={17} />
            View impact
          </Link>
        }
      />

      <div className="grid cols-3" style={{ marginBottom: 18 }}>
        <div className="stat accent">
          <div className="stat-label">Collected, weighed on site</div>
          <div className="stat-value mono">{kg(history.data?.totalKg ?? 0)}</div>
          <div className="stat-hint">Across {entries?.totalElements ?? 0} recorded collections</div>
        </div>
        {(history.data?.byCategory ?? []).slice(0, 2).map((category) => (
          <div className="stat" key={category.code}>
            <div className="stat-label">{category.name}</div>
            <div className="stat-value mono">{kg(category.kg)}</div>
            <div className="stat-hint">{category.code}</div>
          </div>
        ))}
      </div>

      <Card title="Filters">
        <div className="inline-fields">
          <Field label="Material" htmlFor="h-material">
            <select id="h-material" value={material} onChange={(e) => setMaterial(e.target.value)}>
              <option value="">All materials</option>
              {categories.data?.map((category) => (
                <option key={category.id} value={category.code}>
                  {category.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Pickup status" htmlFor="h-status">
            <select id="h-status" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              <option value="RECOVERED">Recovered</option>
              <option value="RECYCLED">Recycled</option>
              <option value="PROCESSING">Processing</option>
              <option value="PICKED_UP">Picked up</option>
            </select>
          </Field>
          <Field label="From" htmlFor="h-from">
            <input id="h-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </Field>
          <Field label="To" htmlFor="h-to">
            <input id="h-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </Field>
        </div>
        <div className="btn-row">
          <Button type="button" onClick={apply}>
            <Icon name="filter" size={16} />
            Apply filters
          </Button>
          {filtered ? (
            <Button type="button" className="ghost" onClick={clear}>
              Clear
            </Button>
          ) : null}
        </div>
      </Card>

      {history.error ? <Note tone="error">{history.error}</Note> : null}
      {history.loading ? (
        <Loading label="Loading your history…" />
      ) : (entries?.content.length ?? 0) === 0 ? (
        <EmptyState
          icon={<Icon name="archive" size={20} />}
          title={filtered ? 'No collections matched those filters' : 'Nothing collected yet'}
          action={
            filtered ? (
              <Button type="button" className="secondary small" onClick={clear}>
                Clear filters
              </Button>
            ) : undefined
          }
        >
          {filtered
            ? 'Try a wider date range or a different material.'
            : 'When a collector weighs and records your waste, it appears here with the exact kilograms.'}
        </EmptyState>
      ) : (
        <Card className="tight">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Material</th>
                  <th>Weight</th>
                  <th>Pickup</th>
                  <th>Collector</th>
                  <th>Notes</th>
                </tr>
              </thead>
              <tbody>
                {entries?.content.map((entry) => (
                  <tr key={entry.id}>
                    <td className="nowrap">{dateTime(entry.collectionDate)}</td>
                    <td>
                      <span className="chip">
                        <span className="swatch" style={{ background: entry.categoryColor ?? '#6B705C' }} aria-hidden="true" />
                        {entry.categoryName}
                      </span>
                    </td>
                    <td className="mono strong">{kg(entry.quantityKg)}</td>
                    <td>
                      <Link to={`/pickups/${entry.pickupCode}`} className="mono">
                        {entry.pickupCode}
                      </Link>
                      <div className="list-meta">{entry.pickupStatus.toLowerCase()}</div>
                    </td>
                    <td>{entry.collectorOrganization ?? '—'}</td>
                    <td className="muted small">{entry.notes ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="btn-row" style={{ marginTop: 16 }}>
            <Button
              type="button"
              className="secondary small"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Newer
            </Button>
            <span className="small muted">
              Page {page + 1} of {Math.max(1, entries?.totalPages ?? 1)}
              {filters.from ? ` · from ${dateOnly(filters.from)}` : ''}
              {filters.to ? ` · to ${dateOnly(filters.to)}` : ''}
            </span>
            <Button
              type="button"
              className="secondary small"
              disabled={entries ? page >= entries.totalPages - 1 : true}
              onClick={() => setPage((p) => p + 1)}
            >
              Older →
            </Button>
          </div>
        </Card>
      )}
    </>
  );
}
