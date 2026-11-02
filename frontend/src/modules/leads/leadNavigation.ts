/**
 * Navegação entre leads da lista que o operador está trabalhando: o detalhe
 * oferece "anterior" e "próximo" na mesma ordem e com os mesmos filtros.
 *
 * Fica no `sessionStorage` da aba: é conveniência de quem usa, não dado de
 * negócio. Sem armazenamento (janela anônima, bloqueio), o detalhe só não
 * mostra os atalhos.
 */

const KEY = 'jh-prospect:leads.navigation';

export interface LeadNavigation {
  /** Ids da página aberta, na ordem da lista. */
  readonly ids: readonly string[];
  /** Query string da lista, para voltar a ela com os mesmos filtros. */
  readonly query: string;
}

export function saveNavigation(navigation: LeadNavigation): void {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(navigation));
  } catch {
    // Sem armazenamento, o detalhe fica sem anterior/próximo.
  }
}

export function readNavigation(): LeadNavigation | null {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    if (raw === null) {
      return null;
    }
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'ids' in parsed &&
      'query' in parsed &&
      Array.isArray(parsed.ids) &&
      parsed.ids.every((id: unknown) => typeof id === 'string') &&
      typeof parsed.query === 'string'
    ) {
      return { ids: parsed.ids.filter((id: unknown): id is string => typeof id === 'string'), query: parsed.query };
    }
    return null;
  } catch {
    return null;
  }
}

/** Vizinhos do lead na lista; ausente se o lead não está nela. */
export function neighbours(
  navigation: LeadNavigation | null,
  leadId: string,
): { readonly previous?: string; readonly next?: string; readonly position: number; readonly total: number } | null {
  if (navigation === null) {
    return null;
  }
  const index = navigation.ids.indexOf(leadId);
  if (index < 0) {
    return null;
  }
  const previous = navigation.ids[index - 1];
  const next = navigation.ids[index + 1];
  return {
    ...(previous === undefined ? {} : { previous }),
    ...(next === undefined ? {} : { next }),
    position: index + 1,
    total: navigation.ids.length,
  };
}
