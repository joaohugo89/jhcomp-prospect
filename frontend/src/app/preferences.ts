import { useState } from 'react';

/**
 * Preferência de tela guardada no navegador (modo compacto, colunas
 * recolhidas). É conveniência de quem usa: sem armazenamento (janela anônima,
 * dados bloqueados), a tela funciona com o padrão. Nunca guardar aqui dado de
 * negócio nem nada que precise valer em outro aparelho.
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
      // Sem armazenamento a preferência vale só nesta visita.
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

/** Prefixo próprio: o `localStorage` de `localhost` é compartilhado com outros projetos da máquina. */
function storageKey(key: string): string {
  return `jh-prospect:${key}`;
}
