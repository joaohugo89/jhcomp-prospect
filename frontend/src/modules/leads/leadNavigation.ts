/**
 * Navigation between the leads of the list the operator is working: the
 * detail offers previous and next in the same order and under the same
 * filters.
 *
 * It lives in the tab's `sessionStorage`: a convenience, not business data.
 * Without storage — a private window, blocked data — the detail simply does
 * not show the shortcuts.
 */

const KEY = 'jh-prospect:leads.navigation';

export interface LeadNavigation {
  /** Ids of the open page, in list order. */
  readonly ids: readonly string[];
  /** The list's query string, to return to it with the same filters. */
  readonly query: string;
}

export function saveNavigation(navigation: LeadNavigation): void {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(navigation));
  } catch {
    // Without storage the detail simply has no previous/next.
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

/** The lead's neighbours in the list; absent when the lead is not in it. */
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
