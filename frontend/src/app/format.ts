/** Formatação de exibição. Datas chegam em UTC (RNF-009) e são mostradas no fuso do navegador. */

const DATE_TIME = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '—' : DATE_TIME.format(date);
}

/**
 * `href` seguro para uma URL vinda da API.
 *
 * O backend já recusa esquemas que não sejam http/https, mas o link é
 * montado aqui de novo, por defesa em profundidade: um dado antigo, importado
 * ou gravado por outro caminho não pode virar `javascript:` no clique.
 *
 * Sem `://`, prefixa `https://` e deixa o parser do navegador decidir: um
 * `javascript:...` vira host com porta inválida e é descartado, enquanto
 * `loja.test:8080` continua válido. O que decide é o protocolo final.
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

/** Data local AAAA-MM-DD, para campos `date`. */
export function localDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Data e hora local AAAA-MM-DDTHH:mm, para campos `datetime-local`. */
export function localDateTime(date: Date): string {
  return `${localDate(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** Valor de `datetime-local` (hora local) como instante UTC; vazio ou inválido vira `undefined`. */
export function toInstant(value: string): string | undefined {
  if (value === '') {
    return undefined;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

/** Iniciais para avatar: "Maria da Silva" vira "MS"; partículas como "da" e "de" não contam. */
export function initials(name: string): string {
  const parts = name
    .trim()
    .split(/\s+/)
    .filter((part) => part.length > 2 || /^[A-ZÀ-Ý]/.test(part));
  const first = parts[0]?.[0] ?? '?';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return `${first}${last}`.toUpperCase();
}
