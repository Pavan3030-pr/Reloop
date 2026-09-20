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

export function todayISO(offsetDays = 0): string {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export function toLocalDateTimeInput(value: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
}
