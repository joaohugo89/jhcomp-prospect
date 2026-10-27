import { useCallback, useEffect, useEffectEvent, useState } from 'react';

import { HttpError } from '../services/httpClient';

/**
 * Estado de uma leitura da API, com os casos exigidos pelo doc 08 §11.
 *
 * Sessão expirada não aparece aqui: é tratada uma única vez pela aplicação,
 * que volta ao login (ver `onSessionExpired`).
 */
export type Load<T> =
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready'; readonly data: T }
  | { readonly kind: 'notFound' }
  | { readonly kind: 'forbidden' }
  | { readonly kind: 'error' };

/**
 * Carrega dados e recarrega quando `key` muda ou `reload` é chamado.
 *
 * `key` é uma chave textual dos parâmetros: a requisição depende de um valor
 * comparável por igualdade, e não da função `loader`, recriada a cada
 * renderização.
 */
export function useLoad<T>(
  key: string,
  loader: (signal: AbortSignal) => Promise<T>,
): readonly [Load<T>, () => void] {
  const [state, setState] = useState<Load<T>>({ kind: 'loading' });
  const [version, setVersion] = useState(0);

  // Lê sempre o loader mais recente sem que ele seja dependência do efeito.
  const load = useEffectEvent((signal: AbortSignal) => loader(signal));

  useEffect(() => {
    const controller = new AbortController();
    setState({ kind: 'loading' });

    load(controller.signal)
      .then((data) => {
        setState({ kind: 'ready', data });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setState(toFailure(cause));
      });

    return () => {
      controller.abort();
    };
  }, [key, version]);

  const reload = useCallback(() => {
    setVersion((current) => current + 1);
  }, []);

  return [state, reload];
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
