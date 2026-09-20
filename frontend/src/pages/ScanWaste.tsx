import { useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Gauge, Loading, Note, PageHead, Spinner, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, AiUnavailableError, ApiError } from '../lib/api';
import { percent, relativeTime } from '../lib/format';
import type { WasteAnalysis } from '../lib/types';

export function ScanWaste() {
  const categories = useAsync(() => api.categories(), []);
  const recent = useAsync(() => api.scans(0, 6), []);
  const fileInput = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysis, setAnalysis] = useState<WasteAnalysis | null>(null);
  const [aiNotice, setAiNotice] = useState<string | null>(null);

  const [categoryId, setCategoryId] = useState('');
  const [detectedItem, setDetectedItem] = useState('');
  const [recyclable, setRecyclable] = useState(true);
  const [hazardous, setHazardous] = useState(false);
  const [instruction, setInstruction] = useState('');
  const [confidence, setConfidence] = useState<number | null>(null);
  const [rawResponse, setRawResponse] = useState<string | null>(null);

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedMessage, setSavedMessage] = useState<string | null>(null);

  const activeCategories = useMemo(() => categories.data?.filter((c) => c.active) ?? [], [categories.data]);
  const selectedCategory = activeCategories.find((c) => c.id === categoryId) ?? null;

  const reset = () => {
    setFile(null);
    setPreviewUrl(null);
    setAnalysis(null);
    setAiNotice(null);
    setCategoryId('');
    setDetectedItem('');
    setRecyclable(true);
    setHazardous(false);
    setInstruction('');
    setConfidence(null);
    setRawResponse(null);
    setSaveError(null);
    if (fileInput.current) fileInput.current.value = '';
  };

  const pickFile = (selected: File | null) => {
    setSavedMessage(null);
    setSaveError(null);
    setAnalysis(null);
    setAiNotice(null);
    setFile(selected);
    setPreviewUrl(selected ? URL.createObjectURL(selected) : null);
  };

  const analyze = async () => {
    if (!file) return;
    setAnalyzing(true);
    setAiNotice(null);
    setSaveError(null);
    try {
      const result = await api.analyze(file);
      setAnalysis(result);
      setDetectedItem(result.item ?? '');
      setInstruction(result.disposalInstruction ?? '');
      setRecyclable(result.recyclable);
      setHazardous(result.hazardous);
      setConfidence(result.confidence);
      setRawResponse(result.aiRawResponse ?? null);
      const match = activeCategories.find((c) => c.code === result.categoryCode);
      setCategoryId(match?.id ?? '');
      if (result.lowConfidence) {
        setAiNotice(
          'The AI was not confident about this item. Please review the category before saving — or pick one yourself.',
        );
      }
    } catch (err) {
      if (err instanceof AiUnavailableError) {
        // The backend reports 503 when the AI provider is not configured or unreachable.
        // Nothing is fabricated — the user classifies the item manually instead.
        setAiNotice(err.message);
      } else if (err instanceof ApiError) {
        setAiNotice(err.message);
      } else {
        setAiNotice('AI analysis failed. You can still classify the item manually.');
      }
    } finally {
      setAnalyzing(false);
    }
  };

  const save = async () => {
    if (!categoryId) {
      setSaveError('Choose a waste category before saving.');
      return;
    }
    setSaving(true);
    setSaveError(null);
    setSavedMessage(null);
    try {
      const scan = await api.saveScan({
        categoryId,
        detectedItem: detectedItem || selectedCategory?.name,
        confidence,
        recyclable,
        hazardous,
        disposalInstruction: instruction || selectedCategory?.defaultDisposalInstructions || undefined,
        aiRawResponse: rawResponse,
        image: file,
      });
      setSavedMessage(`Saved “${scan.detectedItem ?? scan.category?.name}” to your scan history.`);
      reset();
      recent.reload();
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : 'Could not save this scan.');
    } finally {
      setSaving(false);
    }
  };

  const stageClass = analyzing ? 'scan-stage analyzing' : 'scan-stage';

  return (
    <>
      <PageHead
        eyebrow="AI identification"
        title="Scan waste"
        lede="Photograph an item and ReLoop will identify the material, tell you whether it is recyclable or hazardous, and explain how to prepare it. If the AI service is unavailable you can classify it yourself — nothing is guessed on your behalf."
        actions={
          <Link className="btn secondary" to="/points">
            <Icon name="pin" size={17} />
            Collection points
          </Link>
        }
      />

      <div className="grid cols-2">
        <div>
          <Card
            title={
              <>
                <span className="step-no" style={{ marginRight: 8 }}>
                  01
                </span>
                Photograph the item
              </>
            }
          >
            <label
              className={`dropzone${dragging ? ' dragging' : ''}`}
              htmlFor="scan-file"
              onDragOver={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragging(false);
                pickFile(event.dataTransfer.files?.[0] ?? null);
              }}
            >
              <input
                id="scan-file"
                ref={fileInput}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                capture="environment"
                onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
              />
              {previewUrl ? (
                <img className="preview" src={previewUrl} alt="Selected waste item" />
              ) : (
                <>
                  <span className="dz-icon" aria-hidden="true">
                    <Icon name="camera" size={22} />
                  </span>
                  <div className="strong">Take a photo or drop an image here</div>
                  <div className="small muted">JPG, PNG or WEBP up to 8 MB</div>
                </>
              )}
            </label>

            <div className="btn-row" style={{ marginTop: 16 }}>
              <Button type="button" onClick={analyze} disabled={!file || analyzing || activeCategories.length === 0}>
                {analyzing ? <Spinner onPrimary /> : <Icon name="sparkles" size={17} />}
                {analyzing ? 'Analysing…' : 'Analyse with AI'}
              </Button>
              {file ? (
                <Button type="button" className="secondary" onClick={reset}>
                  Clear
                </Button>
              ) : null}
            </div>

            {analyzing ? (
              <div className={stageClass} style={{ marginTop: 16 }}>
                <span className="scan-sweep" aria-hidden="true" />
                <div style={{ padding: 26, width: '100%' }}>
                  <div className="skeleton" style={{ width: '46%', marginBottom: 10 }} />
                  <div className="skeleton" style={{ width: '72%', marginBottom: 10 }} />
                  <div className="skeleton" style={{ width: '38%' }} />
                  <div className="small muted" style={{ marginTop: 14 }} role="status">
                    Reading the image and matching it to a waste category…
                  </div>
                </div>
              </div>
            ) : null}

            {aiNotice ? (
              <div style={{ marginTop: 16 }}>
                <Note tone="warning">{aiNotice}</Note>
              </div>
            ) : null}

            {analysis && !analyzing ? (
              <div style={{ marginTop: 18 }}>
                <div className="panel soft" style={{ padding: 18 }}>
                  <div className="card-head">
                    <h3 style={{ margin: 0 }}>Identified as {analysis.item}</h3>
                    <span className="spacer" />
                    <Gauge value={analysis.confidence} label={`Confidence ${percent(analysis.confidence)}`} />
                  </div>

                  <dl className="readout">
                    <div className="readout-row">
                      <dt>Category</dt>
                      <dd>{analysis.categoryName}</dd>
                    </div>
                    <div className="readout-row">
                      <dt>Recyclable</dt>
                      <dd>
                        {analysis.recyclable ? (
                          <span className="pill green">
                            <Icon name="check" size={12} />
                            Yes
                          </span>
                        ) : (
                          <span className="pill grey">No</span>
                        )}
                      </dd>
                    </div>
                    <div className="readout-row">
                      <dt>Hazardous</dt>
                      <dd>
                        {analysis.hazardous ? (
                          <span className="pill red">
                            <Icon name="alert" size={12} />
                            Needs special handling
                          </span>
                        ) : (
                          <span className="pill grey">No</span>
                        )}
                      </dd>
                    </div>
                    <div className="readout-row">
                      <dt>Guidance</dt>
                      <dd>{analysis.disposalInstruction ?? '—'}</dd>
                    </div>
                    {analysis.aiModel ? (
                      <div className="readout-row">
                        <dt>Model</dt>
                        <dd className="mono">{analysis.aiModel}</dd>
                      </div>
                    ) : null}
                  </dl>

                  <div className="btn-row" style={{ marginTop: 16 }}>
                    <Link className="btn small" to="/points">
                      Find collection options
                      <Icon name="arrowRight" size={15} />
                    </Link>
                    <span className="small muted">Review the category below before saving.</span>
                  </div>
                </div>
              </div>
            ) : null}
          </Card>

          <Card
            title={
              <>
                <span className="step-no" style={{ marginRight: 8 }}>
                  02
                </span>
                Confirm the details
              </>
            }
          >
            {categories.error ? <Note tone="error">{categories.error}</Note> : null}
            {saveError ? <Note tone="error">{saveError}</Note> : null}
            {savedMessage ? (
              <Note tone="success">
                {savedMessage}{' '}
                <Link to="/pickups">Book a pickup for this material →</Link>
              </Note>
            ) : null}

            <Field label="Waste category" htmlFor="category" hint="Choose the closest match">
              <select
                id="category"
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                disabled={categories.loading}
              >
                <option value="">Select a category…</option>
                {activeCategories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Item name" htmlFor="item" hint="What is it? e.g. “Plastic milk bottle”">
              <input
                id="item"
                value={detectedItem}
                onChange={(e) => setDetectedItem(e.target.value)}
                placeholder={selectedCategory?.name ?? 'Short description'}
              />
            </Field>

            <div className="inline wrap" style={{ gap: 20, marginBottom: 16 }}>
              <div className="check">
                <input
                  id="recyclable"
                  type="checkbox"
                  checked={recyclable}
                  onChange={(e) => setRecyclable(e.target.checked)}
                />
                <label htmlFor="recyclable">Recyclable</label>
              </div>
              <div className="check">
                <input id="hazardous" type="checkbox" checked={hazardous} onChange={(e) => setHazardous(e.target.checked)} />
                <label htmlFor="hazardous">Hazardous — needs special handling</label>
              </div>
            </div>

            <Field
              label="How to prepare it"
              htmlFor="instruction"
              hint={selectedCategory?.defaultDisposalInstructions ?? 'Add any preparation notes'}
            >
              <textarea id="instruction" value={instruction} onChange={(e) => setInstruction(e.target.value)} />
            </Field>

            <div className="small muted" style={{ marginBottom: 16 }}>
              {confidence !== null
                ? `Will be saved as an AI-assisted scan (confidence ${percent(confidence)}).`
                : 'Will be saved as a manual scan.'}
            </div>

            <div className="btn-row">
              <Button type="button" onClick={save} disabled={saving || !categoryId}>
                {saving ? <Spinner onPrimary /> : null}
                {saving ? 'Saving…' : 'Save scan'}
              </Button>
              {selectedCategory ? <span className="small muted">{selectedCategory.name}</span> : null}
            </div>
          </Card>
        </div>

        <div>
          <Card
            title="Recent scans"
            action={
              <Button type="button" className="ghost small" onClick={recent.reload}>
                Refresh
              </Button>
            }
          >
            {recent.error ? <Note tone="error">{recent.error}</Note> : null}
            {recent.loading ? (
              <Loading />
            ) : (recent.data?.content.length ?? 0) === 0 ? (
              <EmptyState icon={<Icon name="archive" size={20} />} title="No scans recorded yet">
                Your saved scans will appear here with their source and confidence.
              </EmptyState>
            ) : (
              <div className="list">
                {recent.data?.content.map((scan) => (
                  <div className="list-item" key={scan.id}>
                    {scan.imageUrl ? <img className="thumb sm" src={scan.imageUrl} alt="" /> : null}
                    <div className="grow">
                      <div className="list-title">{scan.detectedItem ?? scan.category?.name ?? 'Scan'}</div>
                      <div className="list-meta">
                        {scan.category?.name ?? 'Uncategorised'} · {scan.source === 'AI' ? 'AI-assisted' : 'Manual'}
                        {scan.confidence !== null ? ` · ${percent(scan.confidence)}` : ''} · {relativeTime(scan.createdAt)}
                      </div>
                      {scan.disposalInstruction ? (
                        <div className="small muted" style={{ marginTop: 4 }}>
                          {scan.disposalInstruction}
                        </div>
                      ) : null}
                    </div>
                    {scan.hazardous ? (
                      <span className="pill red">
                        <Icon name="alert" size={12} />
                        Hazard
                      </span>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </Card>

          <Card title="Category guide">
            {categories.loading ? (
              <Loading />
            ) : (
              <div className="list">
                {activeCategories.map((category) => (
                  <div className="list-item" key={category.id}>
                    <span
                      className="swatch"
                      style={{ background: category.colorHex ?? '#6B705C', width: 12, height: 12, borderRadius: 4, marginTop: 6 }}
                      aria-hidden="true"
                    />
                    <div className="grow">
                      <div className="list-title">{category.name}</div>
                      <div className="list-meta">{category.description}</div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </>
  );
}
