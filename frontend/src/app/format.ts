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
