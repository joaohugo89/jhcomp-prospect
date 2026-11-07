import '@testing-library/jest-dom/vitest';

// jsdom does not implement scrolling; the lead detail's actions scroll to the field before focusing.
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = (): void => undefined;
}

// jsdom does not implement matchMedia; the theme reads `prefers-color-scheme`
// to resolve the device's preferred mode.
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
