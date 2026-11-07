/** Display formatting. Dates arrive in UTC (RNF-009) and are shown in the browser's time zone. */

const DATE_TIME = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '—' : DATE_TIME.format(date);
}

/**
 * A safe `href` for a URL coming from the API.
 *
 * The backend already refuses schemes other than http/https, but the link is
 * built again here as defence in depth: an old value, imported or written
 * through some other path, must not become `javascript:` on click.
 *
 * Without `://` it prefixes `https://` and lets the browser's parser decide: a
 * `javascript:...` becomes a host with an invalid port and is discarded, while
 * `loja.test:8080` stays valid. The final protocol is what decides.
 */
export function safeHref(url: string | undefined): string | undefined {
  if (url === undefined || url.trim() === '') {
    return undefined;
  }
  const value = url.trim();
  const candidate = value.includes('://') ? value : `https://${value}`;

  try {
    const parsed = new URL(candidate);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : undefined;
  } catch {
    return undefined;
  }
}

const CURRENCY = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

export function formatCurrency(value: number): string {
  return CURRENCY.format(value);
}

const pad = (value: number): string => String(value).padStart(2, '0');

/** Local date as YYYY-MM-DD, for `date` inputs. */
export function localDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Local date and time as YYYY-MM-DDTHH:mm, for `datetime-local` inputs. */
export function localDateTime(date: Date): string {
  return `${localDate(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A `datetime-local` value (local time) as a UTC instant; empty or invalid becomes `undefined`. */
export function toInstant(value: string): string | undefined {
  if (value === '') {
    return undefined;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

/** Avatar initials: "Maria da Silva" becomes "MS"; particles such as "da" and "de" do not count. */
export function initials(name: string): string {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter((part) => part.length > 2 || /^[A-ZÀ-Ý]/.test(part));
  const first = parts[0]?.[0] ?? '?';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return `${first}${last}`.toUpperCase();
}
