import '@testing-library/jest-dom/vitest';

// O jsdom não implementa rolagem; as ações do detalhe do lead rolam até o campo antes de focar.
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = (): void => undefined;
}
