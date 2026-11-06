import { useCallback, useState } from 'react';

/**
 * Modo claro (creme) e escuro (vinho). São só esses dois: sem escolha
 * guardada vale a preferência do aparelho, e o botão alterna um e outro.
 *
 * O tema é aplicado como `data-theme` no <html>, e `index.css` traz o modo
 * escuro nesse seletor. A escrita acontece na carga do módulo, antes do
 * primeiro render, porque a CSP desta aplicação não permite script inline
 * (`script-src 'self'`): não há como aplicar o tema antes do bundle.
 */
export type Theme = 'LIGHT' | 'DARK';

const STORAGE_KEY = 'jh-prospect:theme';

function systemTheme(): Theme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'DARK' : 'LIGHT';
}

/** O tema guardado; sem escolha (ou sem armazenamento), o do aparelho. */
export function readTheme(): Theme {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw === 'LIGHT' || raw === 'DARK' ? raw : systemTheme();
  } catch {
    // Janela anônima ou dados bloqueados: vale o do aparelho.
    return systemTheme();
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.dataset['theme'] = theme === 'DARK' ? 'dark' : 'light';
}

/** Aplica o tema guardado. Chamado uma vez, antes do primeiro render. */
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
      // A escolha vale só nesta visita.
    }
    applyTheme(next);
  }, []);

  return [theme, change];
}
