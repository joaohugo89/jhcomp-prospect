import '@testing-library/jest-dom/vitest';

// O jsdom não implementa rolagem; as ações do detalhe do lead rolam até o campo antes de focar.
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = (): void => undefined;
}

// O jsdom não implementa matchMedia; o seletor de tema consulta
// `prefers-color-scheme` para resolver o modo "do sistema".
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList;
}
