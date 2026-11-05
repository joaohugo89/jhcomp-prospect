import { lazy, Suspense, useEffect, useState } from 'react';

import { Link, useRoute, type Route } from './app/router';
import { AppShell } from './components/AppShell';
import { EmptyState, LoadingState } from './components/States';
import { buttonPrimary } from './components/styles';
import { LoginPage } from './modules/auth/LoginPage';
import { ForcedPasswordChange, PasswordPage } from './modules/auth/PasswordPages';
import { CompaniesPage } from './modules/companies/CompaniesPage';
import { CompanyDetailPage } from './modules/companies/CompanyDetailPage';
import { CompanyNewPage } from './modules/companies/CompanyNewPage';
import { PipelinePage } from './modules/crm/PipelinePage';
import { DashboardPage } from './modules/dashboard/DashboardPage';

// Só em desenvolvimento: no build de produção a condição é `false`, o import some
// e o mock não chega ao pacote publicado.
const DashboardPreviewPage = import.meta.env.DEV
  ? lazy(async () => ({ default: (await import('./modules/dashboard/DashboardPreviewPage')).DashboardPreviewPage }))
  : null;
import { ImportDetailPage } from './modules/imports/ImportDetailPage';
import { ImportsPage } from './modules/imports/ImportsPage';
import { LeadDetailPage } from './modules/leads/LeadDetailPage';
import { LeadsPage } from './modules/leads/LeadsPage';
import { MembersPage } from './modules/members/MembersPage';
import { SuppressionPage } from './modules/outreach/SuppressionPage';
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
      <main className="min-h-dvh bg-stone-50">
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

  async function signOut(): Promise<void> {
    try {
      await logout();
    } finally {
      // Mesmo que a chamada falhe, a interface volta ao login: insistir em
      // mostrar uma sessão que talvez não exista mais seria pior.
      setAuth({ kind: 'anonymous', expired: false });
    }
  }

  function updateSession(session: Session): void {
    setAuth({ kind: 'authenticated', session });
  }

  if (auth.session.passwordChangeRequired) {
    return (
      <ForcedPasswordChange
        session={auth.session}
        onChanged={updateSession}
        onSignOut={() => {
          void signOut();
        }}
      />
    );
  }

  return (
    <AppShell session={auth.session} route={route} onSignOut={signOut}>
      <Screen route={route} session={auth.session} onSessionChanged={updateSession} />
    </AppShell>
  );
}

function Screen({
  route,
  session,
  onSessionChanged,
}: {
  readonly route: Route;
  readonly session: Session;
  readonly onSessionChanged: (session: Session) => void;
}): React.JSX.Element {
  switch (route.name) {
    case 'dashboard':
      return <DashboardPage session={session} />;
    case 'dashboardPreview':
      return DashboardPreviewPage === null ? (
        <EmptyState title="Página não encontrada." />
      ) : (
        <Suspense fallback={<LoadingState />}>
          <DashboardPreviewPage />
        </Suspense>
      );
    case 'pipeline':
      return <PipelinePage />;
    case 'leads':
      // key: outro filtro na URL (drill-down do dashboard) é outra lista.
      return <LeadsPage key={window.location.search} session={session} />;
    case 'lead':
      // key: trocar de lead recria a tela, sem herdar estado do anterior.
      return <LeadDetailPage key={route.id} id={route.id} />;
    case 'companies':
      return <CompaniesPage key={window.location.search} />;
    case 'companyNew':
      return <CompanyNewPage />;
    case 'company':
      return <CompanyDetailPage key={route.id} id={route.id} />;
    case 'imports':
      return <ImportsPage />;
    case 'import':
      return <ImportDetailPage key={route.id} id={route.id} />;
    case 'members':
      return <MembersPage session={session} />;
    case 'suppression':
      return <SuppressionPage session={session} />;
    case 'password':
      return <PasswordPage onChanged={onSessionChanged} />;
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
