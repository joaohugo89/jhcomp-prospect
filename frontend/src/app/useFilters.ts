import { useState } from 'react';

/**
 * Listing filters: the form draft, the applied filters and the page.
 *
 * Draft and applied filters are kept apart so the search only fires when the
 * form is submitted, not on every keystroke. Applying returns to the first
 * page, because the current page may not exist in the new result.
 *
 * @param empty filters with no effect; must be a module-level constant
 * @param initial filters already applied on open (read from the URL, say)
 */
export function useFilters<F>(empty: F, initial: F = empty) {
  const [draft, setDraft] = useState<F>(initial);
  const [applied, setApplied] = useState<F>(initial);
  const [page, setPage] = useState(0);

  return {
    draft,
    setDraft,
    applied,
    page,
    setPage,
    apply: (): void => {
      setPage(0);
      setApplied(draft);
    },
    /** Distinguishes "nothing registered" from "nothing matches the filters". */
    isFiltered: JSON.stringify(applied) !== JSON.stringify(empty),
    /** The query key for `useLoad`. */
    queryKey: JSON.stringify({ applied, page }),
  };
}
