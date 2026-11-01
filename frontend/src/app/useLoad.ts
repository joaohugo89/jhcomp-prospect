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
export function useLoad<T>(key: string, loader: (signal: AbortSignal) => Promise<T>): readonly [Load<T>, () => void] {
  const [version, setVersion] = useState(0);
  // Resultado guardado com a chave e a versão que o produziram. Enquanto a
  // requisição atual não responde, o estado é derivado, sem setState síncrono
  // dentro do efeito, que custaria uma renderização extra.
  const [result, setResult] = useState<{
    readonly key: string;
    readonly version: number;
    readonly load: Load<T>;
  } | null>(null);

  // Lê sempre o loader mais recente sem que ele seja dependência do efeito.
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
 * Estado a exibir.
 *
 * Ao recarregar a mesma chave (depois de uma alteração ou durante
 * acompanhamento de um processo), o último dado pronto continua na tela até o
 * novo chegar: voltar a "Carregando…" faria a tela piscar a cada consulta.
 * Chave nova é outra consulta, e aí sim mostra carregando.
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
 * Último dado pronto, mantido enquanto uma chave nova carrega (outra página,
 * outro filtro). Trocar o conteúdo por "Carregando…" encolhe a tela, puxa a
 * rolagem para cima e tira o foco do botão clicado.
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
