import { useCallback, useEffect, useEffectEvent, useState } from 'react';

import { HttpError } from '../services/httpClient';

/**
 * The state of a read from the API, covering the cases doc 08 §11 requires.
 *
 * An expired session does not appear here: it is handled once by the
 * application, which returns to the login screen (see `onSessionExpired`).
 */
export type Load<T> =
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready'; readonly data: T }
  | { readonly kind: 'notFound' }
  | { readonly kind: 'forbidden' }
  | { readonly kind: 'error' };

/**
 * Loads data, and reloads when `key` changes or `reload` is called.
 *
 * `key` is a textual key over the parameters: the request depends on a value
 * comparable by equality, not on the `loader` function, which is recreated on
 * every render.
 */
export function useLoad<T>(key: string, loader: (signal: AbortSignal) => Promise<T>): readonly [Load<T>, () => void] {
  const [version, setVersion] = useState(0);
  // The result is stored with the key and version that produced it. While the
  // current request is in flight the state is derived, with no synchronous
  // setState inside the effect, which would cost an extra render.
  const [result, setResult] = useState<{
    readonly key: string;
    readonly version: number;
    readonly load: Load<T>;
  } | null>(null);

  // Always reads the latest loader without making it a dependency of the effect.
  const load = useEffectEvent((signal: AbortSignal) => loader(signal));

  useEffect(() => {
    const controller = new AbortController();

    load(controller.signal)
      .then((data) => {
        setResult({ key, version, load: { kind: 'ready', data } });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setResult({ key, version, load: toFailure(cause) });
      });

    return () => {
      controller.abort();
    };
  }, [key, version]);

  const reload = useCallback(() => {
    setVersion((current) => current + 1);
  }, []);

  return [stateOf(result, key, version), reload];
}

/**
 * The state to display.
 *
 * When reloading the same key — after a change, or while following a running
 * process — the last ready data stays on screen until the new data arrives:
 * falling back to a loading state would make the screen flash on every poll.
 * A new key is a different query, and that does show the loading state.
 */
function stateOf<T>(
  result: { readonly key: string; readonly version: number; readonly load: Load<T> } | null,
  key: string,
  version: number,
): Load<T> {
  if (result === null || result.key !== key) {
    return { kind: 'loading' };
  }
  return result.version === version || result.load.kind === 'ready' ? result.load : { kind: 'loading' };
}

/**
 * The last ready data, kept while a new key loads (another page, another
 * filter). Replacing the content with a loading state shrinks the page, pulls
 * the scroll position up and takes focus away from the button just clicked.
 */
export function useLastReady<T>(state: Load<T>): T | null {
  const [last, setLast] = useState<T | null>(null);
  if (state.kind === 'ready' && state.data !== last) {
    setLast(state.data);
  }
  return state.kind === 'ready' ? state.data : last;
}

function toFailure(cause: unknown): Load<never> {
  if (cause instanceof HttpError) {
    if (cause.status === 404) {
      return { kind: 'notFound' };
    }
    if (cause.status === 403) {
      return { kind: 'forbidden' };
    }
  }
  return { kind: 'error' };
}
