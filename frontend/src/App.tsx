import { useEffect, useState } from 'react';

import { Link, useRoute, type Route } from './app/router';
import { AppShell } from './components/AppShell';
import { EmptyState, LoadingState } from './components/States';
import { buttonPrimary } from './components/styles';
import { LoginPage } from './modules/auth/LoginPage';
import { CompaniesPage } from './modules/companies/CompaniesPage';
import { CompanyDetailPage } from './modules/companies/CompanyDetailPage';
import { CompanyNewPage } from './modules/companies/CompanyNewPage';
import { ImportDetailPage } from './modules/imports/ImportDetailPage';
import { ImportsPage } from './modules/imports/ImportsPage';
import { LeadDetailPage } from './modules/leads/LeadDetailPage';
import { LeadsPage } from './modules/leads/LeadsPage';
import { MembersPage } from './modules/members/MembersPage';
import { fetchSession, logout } from './services/authService';
import { onSessionExpired } from './services/httpClient';
import type { Session } from './types/session';

type AuthState =
  | { readonly kind: 'checking' }
  | { readonly kind: 'anonymous'; readonly expired: boolean }
  | { readonly kind: 'authenticated'; readonly session: Session };

/** Raiz da aplicação: sessão, roteamento e estrutura comum. */
export function App(): React.JSX.Element {
  const [auth, setAuth] = useState<AuthState>({ kind: 'checking' });
  const route = useRoute();

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
          setAuth({ kind: 'anonymous', expired: false });
        }
      });

    return () => {
      controller.abort();
    };
  }, []);

  // Sessão revogada ou expirada no meio do uso (D21): volta ao login com o
  // motivo, em vez de cada tela mostrar um erro genérico.
  useEffect(
    () =>
      onSessionExpired(() => {
        setAuth({ kind: 'anonymous', expired: true });
      }),
    [],
  );

  if (auth.kind === 'checking') {
    return (
      <main className="min-h-dvh bg-slate-50">
        <LoadingState label="Verificando sessão…" />
      </main>
    );
  }

  if (auth.kind === 'anonymous') {
    return (
      <LoginPage
        sessionExpired={auth.expired}
        onAuthenticated={(session) => {
          setAuth({ kind: 'authenticated', session });
        }}
      />
    );
  }

  return (
    <AppShell
      session={auth.session}
      route={route}
      onSignOut={async () => {
        try {
          await logout();
        } finally {
          // Mesmo que a chamada falhe, a interface volta ao login: insistir em
          // mostrar uma sessão que talvez não exista mais seria pior.
          setAuth({ kind: 'anonymous', expired: false });
        }
      }}
    >
      <Screen route={route} />
    </AppShell>
  );
}

function Screen({ route }: { readonly route: Route }): React.JSX.Element {
  switch (route.name) {
    case 'leads':
      return <LeadsPage />;
    case 'lead':
      // key: trocar de lead recria a tela, sem herdar estado do anterior.
      return <LeadDetailPage key={route.id} id={route.id} />;
    case 'companies':
      return <CompaniesPage />;
    case 'companyNew':
      return <CompanyNewPage />;
    case 'company':
      return <CompanyDetailPage key={route.id} id={route.id} />;
    case 'imports':
      return <ImportsPage />;
    case 'import':
      return <ImportDetailPage key={route.id} id={route.id} />;
    case 'members':
      return <MembersPage />;
    case 'notFound':
      return (
        <EmptyState
          title="Página não encontrada."
          action={
            <Link to="/leads" className={buttonPrimary}>
              Ir para leads
            </Link>
          }
        />
      );
  }
}
