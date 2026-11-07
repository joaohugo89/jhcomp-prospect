import { useState } from 'react';

/**
 * A screen preference kept in the browser (compact mode, collapsed columns).
 * It is a convenience for whoever is using it: with no storage — a private
 * window, blocked data — the screen works on the default. Never keep business
 * data here, nor anything that has to hold on another device.
 */
export function usePreference<T>(
  key: string,
  fallback: T,
  parse: (raw: unknown) => T | undefined,
): readonly [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => read(key, parse) ?? fallback);

  function update(next: T): void {
    setValue(next);
    try {
      window.localStorage.setItem(storageKey(key), JSON.stringify(next));
    } catch {
      // Without storage the preference only lasts for this visit.
    }
  }

  return [value, update];
}

function read<T>(key: string, parse: (raw: unknown) => T | undefined): T | undefined {
  try {
    const raw = window.localStorage.getItem(storageKey(key));
    return raw === null ? undefined : parse(JSON.parse(raw));
  } catch {
    return undefined;
  }
}

/** Own prefix: `localStorage` on `localhost` is shared with the other projects on this machine. */
function storageKey(key: string): string {
  return `jh-prospect:${key}`;
}
