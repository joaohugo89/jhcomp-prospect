import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { applyTheme, initTheme, readThemeChoice } from './theme';

/** Simula a preferência do aparelho, que o jsdom não tem. */
function stubSystemDark(dark: boolean): void {
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: dark,
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }));
}

describe('tema', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('sem escolha guardada, segue a preferência do aparelho', () => {
    stubSystemDark(true);
    initTheme();
    expect(document.documentElement.dataset['theme']).toBe('dark');

    stubSystemDark(false);
    initTheme();
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('a escolha explícita vence a preferência do aparelho', () => {
    stubSystemDark(true);
    window.localStorage.setItem('jh-prospect:theme', 'LIGHT');

    initTheme();

    expect(readThemeChoice()).toBe('LIGHT');
    expect(document.documentElement.dataset['theme']).toBe('light');
  });

  it('escolha desconhecida no armazenamento volta para o do sistema', () => {
    stubSystemDark(true);
    window.localStorage.setItem('jh-prospect:theme', 'NEON');

    expect(readThemeChoice()).toBe('SYSTEM');
    applyTheme(readThemeChoice());
    expect(document.documentElement.dataset['theme']).toBe('dark');
  });
});
