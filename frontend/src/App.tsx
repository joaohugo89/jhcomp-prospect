import { useEffect, useState } from 'react';

import { LoginForm } from './modules/auth/LoginForm';
import { SessionPanel } from './modules/auth/SessionPanel';
import { fetchSession } from './services/authService';
import { fetchHealth } from './services/healthService';
import type { Health } from './types/health';
import type { Session } from './types/session';

type AuthState =
  | { readonly kind: 'checking' }
  | { readonly kind: 'anonymous' }
  | { readonly kind: 'authenticated'; readonly session: Session };

/**
 * Casca provisória da aplicação.
 *
 * A Etapa 4 substitui esta tela pelo AppShell, pela Sidebar e pelo Dashboard
 * do doc 08. Por ora ela permite verificar manualmente o que a Etapa 3
 * entregou: login, contexto de organização, restrição por papel e logout.
 */
export function App(): React.JSX.Element {
  const [auth, setAuth] = useState<AuthState>({ kind: 'checking' });
  const [health, setHealth] = useState<Health | null>(null);

  // A sessão é verificada no servidor, e não a partir de algo guardado no
  // navegador: o cookie é HttpOnly e o cliente não tem como inspecioná-lo.
  // Este GET também faz o backend emitir o cookie com o token CSRF, de que o
  // POST de login depende.
  useEffect(() => {
    const controller = new AbortController();

    fetchSession(controller.signal)
      .then((session) => {
        setAuth({ kind: 'authenticated', session });
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setAuth({ kind: 'anonymous' });
        }
      });

    fetchHealth(controller.signal)
      .then(setHealth)
      .catch(() => {
        // Indicador auxiliar; a falha dele não deve atrapalhar o login.
      });

    return () => {
      controller.abort();
    };
  }, []);

  return (
    <main className="min-h-dvh bg-slate-50 px-4 py-10 text-slate-900">
      <div className="mx-auto max-w-xl">
        <header>
          <h1 className="text-2xl font-semibold tracking-tight">JH.COMP Prospect</h1>
          <p className="mt-1 text-sm text-slate-600">
            Ambiente de desenvolvimento. Interface provisória da Etapa 3.
          </p>
        </header>

        <div className="mt-8 rounded-lg border border-slate-200 bg-white p-6">
          {auth.kind === 'checking' && <p className="text-slate-600">Verificando sessão…</p>}

          {auth.kind === 'anonymous' && (
            <>
              <h2 className="mb-4 text-lg font-medium">Entrar</h2>
              <LoginForm
                onAuthenticated={(session) => {
                  setAuth({ kind: 'authenticated', session });
                }}
              />
            </>
          )}

          {auth.kind === 'authenticated' && (
            <SessionPanel
              session={auth.session}
              onSignedOut={() => {
                setAuth({ kind: 'anonymous' });
              }}
            />
          )}
        </div>

        <footer className="mt-6 text-xs text-slate-500">
          API:{' '}
          {health === null ? (
            <span>indisponível</span>
          ) : (
            <span>
              {health.status} · versão {health.version}
            </span>
          )}
        </footer>
      </div>
    </main>
  );
}
