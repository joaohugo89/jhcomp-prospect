import { useCallback, useEffect, useState } from 'react';

/**
 * Modo claro (creme) e escuro (vinho). `SYSTEM` é o padrão e acompanha o
 * aparelho; a escolha explícita fica no `localStorage` desta origem, como as
 * demais preferências de tela — é conveniência, não dado de negócio.
 *
 * O tema é aplicado como `data-theme` no <html>, e `index.css` traz o modo
 * escuro nesse seletor. A escrita acontece na carga do módulo, antes do
 * primeiro render, porque a CSP desta aplicação não permite script inline
 * (`script-src 'self'`): não há como aplicar o tema antes do bundle.
 */
export type ThemeChoice = 'SYSTEM' | 'LIGHT' | 'DARK';

const STORAGE_KEY = 'jh-prospect:theme';

export function readThemeChoice(): ThemeChoice {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw === 'LIGHT' || raw === 'DARK' || raw === 'SYSTEM' ? raw : 'SYSTEM';
  } catch {
    // Sem armazenamento (janela anônima, dados bloqueados) vale o do sistema.
    return 'SYSTEM';
  }
}

function systemPrefersDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

/** Resolve `SYSTEM` para o modo de agora e escreve o atributo no <html>. */
export function applyTheme(choice: ThemeChoice): void {
  const dark = choice === 'DARK' || (choice === 'SYSTEM' && systemPrefersDark());
  document.documentElement.dataset['theme'] = dark ? 'dark' : 'light';
}

function storeThemeChoice(choice: ThemeChoice): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, choice);
  } catch {
    // A escolha vale só nesta visita.
  }
}

/** Aplica o tema guardado. Chamado uma vez, antes do primeiro render. */
export function initTheme(): void {
  applyTheme(readThemeChoice());
}

export function useTheme(): readonly [ThemeChoice, (choice: ThemeChoice) => void] {
  const [choice, setChoice] = useState<ThemeChoice>(() => readThemeChoice());

  // Em `SYSTEM`, acompanhar o aparelho enquanto a tela está aberta.
  useEffect(() => {
    if (choice !== 'SYSTEM') {
      return undefined;
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (): void => {
      applyTheme('SYSTEM');
    };
    query.addEventListener('change', onChange);
    return () => {
      query.removeEventListener('change', onChange);
    };
  }, [choice]);

  const change = useCallback((next: ThemeChoice) => {
    setChoice(next);
    storeThemeChoice(next);
    applyTheme(next);
  }, []);

  return [choice, change];
}
