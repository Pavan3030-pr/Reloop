import type { PickupStatus } from './types';

export function kg(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined) return '—';
  return `${Number(value).toFixed(digits)} kg`;
}

export function num(value: number | null | undefined, digits = 2): string {
  if (value === null || value === undefined) return '—';
  return Number(value).toFixed(digits);
}

export function percent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—';
  return `${Math.round(Number(value) * 100)}%`;
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function dateOnly(value: string | null | undefined): string {
  if (!value) return '—';
  const date = new Date(value.length <= 10 ? `${value}T00:00:00` : value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
}

export function relativeTime(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value).getTime();
  if (Number.isNaN(date)) return '';
  const diff = Date.now() - date;
  const minutes = Math.round(diff / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days}d ago`;
  return dateOnly(value);
}

const STATUS_LABELS: Record<PickupStatus, string> = {
  REQUESTED: 'Requested',
  ACCEPTED: 'Accepted',
  SCHEDULED: 'Scheduled',
  PICKED_UP: 'Picked up',
  PROCESSING: 'Processing',
  RECOVERED: 'Recovered',
  RECYCLED: 'Recycled',
  CANCELLED: 'Cancelled',
};

export function statusLabel(status: PickupStatus | string | null | undefined): string {
  if (!status) return '—';
  return STATUS_LABELS[status as PickupStatus] ?? status;
}

export function statusTone(status: PickupStatus | string | null | undefined): 'green' | 'amber' | 'blue' | 'red' | 'grey' {
  switch (status) {
    case 'REQUESTED':
      return 'amber';
    case 'ACCEPTED':
      return 'blue';
    case 'SCHEDULED':
      return 'blue';
    case 'PICKED_UP':
      return 'blue';
    case 'PROCESSING':
      return 'amber';
    case 'RECOVERED':
      return 'green';
    case 'RECYCLED':
      return 'green';
    case 'CANCELLED':
      return 'red';
    default:
      return 'grey';
  }
}

export const PICKUP_STAGES: { key: string; label: string }[] = [
  { key: 'REQUESTED', label: 'Request submitted' },
  { key: 'ACCEPTED', label: 'Collector accepted' },
  { key: 'SCHEDULED', label: 'Pickup scheduled' },
  { key: 'PICKED_UP', label: 'Collected & weighed' },
  { key: 'PROCESSING', label: 'At the recycling facility' },
  { key: 'RECOVERED', label: 'Material recovered' },
];

/**
 * RECOVERED and RECYCLED are two equally valid terminal outcomes, not consecutive steps, so
 * RECYCLED is deliberately kept out of PICKUP_STAGES and treated as a fully complete journey here.
 */
export function stageProgress(status: PickupStatus | string | null | undefined): number {
  if (!status) return 0;
  if (status === 'RECYCLED') return 100;
  const index = PICKUP_STAGES.findIndex((stage) => stage.key === status);
  return index < 0 ? 0 : ((index + 1) / PICKUP_STAGES.length) * 100;
}

export function stageLabel(status: PickupStatus | string | null | undefined): string {
  if (!status) return '—';
  if (status === 'RECYCLED') return 'Material recycled';
  return PICKUP_STAGES.find((stage) => stage.key === status)?.label ?? statusLabel(status);
}

/** Index of the current stage in the linear journey; RECYCLED counts as the final stage. */
export function stageIndex(status: PickupStatus | string | null | undefined): number {
  if (!status) return -1;
  if (status === 'RECYCLED') return PICKUP_STAGES.length - 1;
  return PICKUP_STAGES.findIndex((stage) => stage.key === status);
}

/**
 * What happens next, in plain language, derived from the real status the API returned. Kept in one
 * place so the resident-facing summary and the timeline can never disagree about the same pickup.
 */
export function pickupNextStep(status: PickupStatus | string | null | undefined, organisation?: string | null): string {
  const who = organisation ?? 'Your collector';
  switch (status) {
    case 'REQUESTED':
      return 'Waiting for a verified collection partner to accept this request.';
    case 'ACCEPTED':
      return `${who} accepted the request and will confirm a visit time.`;
    case 'SCHEDULED':
      return `${who} will collect the material during the scheduled window.`;
    case 'PICKED_UP':
      return 'The material has been weighed on site and is on its way to the facility.';
    case 'PROCESSING':
      return 'The material is being sorted and processed.';
    case 'RECOVERED':
      return 'The material completed recovery. Nothing further is needed from you.';
    case 'RECYCLED':
      return 'The material was recycled into new material. This journey is complete.';
    case 'CANCELLED':
      return 'This request was cancelled, so no collector will visit.';
    default:
      return '—';
  }
}

export function todayISO(offsetDays = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export function toLocalDateTimeInput(value: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
}
