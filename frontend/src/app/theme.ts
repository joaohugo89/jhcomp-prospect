import { useCallback, useState } from 'react';

/**
 * Light mode (cream) and dark mode (oxblood). Only those two: with no stored
 * choice the device preference wins, and the button alternates between them.
 *
 * The theme is applied as `data-theme` on <html>, and `index.css` defines dark
 * mode under that selector. It is written when the module loads, before the
 * first render, because this application's CSP forbids inline script
 * (`script-src 'self'`): there is no way to apply the theme before the bundle.
 */
export type Theme = 'LIGHT' | 'DARK';

const STORAGE_KEY = 'jh-prospect:theme';

function systemTheme(): Theme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'DARK' : 'LIGHT';
}

/** The stored theme; with no choice, or no storage, the device's own. */
export function readTheme(): Theme {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw === 'LIGHT' || raw === 'DARK' ? raw : systemTheme();
  } catch {
    // A private window, or blocked data: the device preference wins.
    return systemTheme();
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset['theme'] = theme === 'DARK' ? 'dark' : 'light';
}

/** Applies the stored theme. Called once, before the first render. */
export function initTheme(): void {
  applyTheme(readTheme());
}

export function useTheme(): readonly [Theme, (theme: Theme) => void] {
  const [theme, setTheme] = useState<Theme>(() => readTheme());

  const change = useCallback((next: Theme) => {
    setTheme(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // The choice only lasts for this visit.
    }
    applyTheme(next);
  }, []);

  return [theme, change];
}
