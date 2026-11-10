import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { server } from '../mocks/server';
import { setAccessToken, setUnauthorizedHandler } from '../api/client';

// jsdom has no layout engine: minimal stubs for browser APIs used by Radix / our hooks.
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub as unknown as typeof ResizeObserver;

/** matchMedia that understands `(min-width: Npx)` against window.innerWidth. */
function stubMatchMedia(query: string): MediaQueryList {
  const min = /min-width:\s*(\d+)px/.exec(query);
  return {
    matches: min ? window.innerWidth >= Number(min[1]) : false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  } as MediaQueryList;
}
window.matchMedia = stubMatchMedia;
Element.prototype.scrollIntoView ??= () => {};
Element.prototype.hasPointerCapture ??= () => false;
Element.prototype.releasePointerCapture ??= () => {};

const DESKTOP_WIDTH = 1280;

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(() => {
  window.innerWidth = DESKTOP_WIDTH;
});
afterEach(() => {
  cleanup();
  server.resetHandlers();
  sessionStorage.clear();
  localStorage.clear();
  setAccessToken(null);
  setUnauthorizedHandler(null);
});
afterAll(() => server.close());
