import { lazy, Suspense, useEffect, useState } from 'react';

import { Link, useRoute, type Route } from './app/router';
import { AppShell } from './components/AppShell';
import { EmptyState, LoadingState } from './components/States';
import { buttonPrimary } from './components/styles';
import { LoginPage } from './modules/auth/LoginPage';
import { ForcedPasswordChange } from './modules/auth/PasswordPages';
import { DashboardPage } from './modules/dashboard/DashboardPage';
import { LeadsPage } from './modules/leads/LeadsPage';
import { fetchSession, logout } from './services/authService';
import { onSessionExpired } from './services/httpClient';
import type { Session } from './types/session';

/*
 * Route-level code splitting.
 *
 * Login, the shell, the dashboard and the lead worklist load eagerly: they are
 * the first screen in almost every session. Everything else is a separate
 * chunk fetched on first navigation, so opening the app does not pay for the
 * drag-and-drop board, the CSV import screens or the admin pages.
 *
 * All chunks are same-origin and covered by the `script-src 'self'` CSP.
 */
const PipelinePage = lazyPage(async () => (await import('./modules/crm/PipelinePage')).PipelinePage);
const LeadDetailPage = lazyPage(async () => (await import('./modules/leads/LeadDetailPage')).LeadDetailPage);
const CompaniesPage = lazyPage(async () => (await import('./modules/companies/CompaniesPage')).CompaniesPage);
const CompanyDetailPage = lazyPage(
  async () => (await import('./modules/companies/CompanyDetailPage')).CompanyDetailPage,
);
const CompanyNewPage = lazyPage(async () => (await import('./modules/companies/CompanyNewPage')).CompanyNewPage);
const ImportsPage = lazyPage(async () => (await import('./modules/imports/ImportsPage')).ImportsPage);
const ImportDetailPage = lazyPage(async () => (await import('./modules/imports/ImportDetailPage')).ImportDetailPage);
const MembersPage = lazyPage(async () => (await import('./modules/members/MembersPage')).MembersPage);
const SuppressionPage = lazyPage(async () => (await import('./modules/outreach/SuppressionPage')).SuppressionPage);
const PasswordPage = lazyPage(async () => (await import('./modules/auth/PasswordPages')).PasswordPage);

/**
 * Development only: in a production build the condition is `false`, the import
 * is dropped and the mock never reaches the published bundle.
 */
const DashboardPreviewPage = import.meta.env.DEV
  ? lazyPage(async () => (await import('./modules/dashboard/DashboardPreviewPage')).DashboardPreviewPage)
  : null;

/** `React.lazy` over a named export, which `lazy` does not accept directly. */
function lazyPage<P>(load: () => Promise<React.ComponentType<P>>): React.LazyExoticComponent<React.ComponentType<P>> {
  return lazy(async () => ({ default: await load() }));
}

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
      {/* One boundary for every lazy route: the shell stays on screen while the chunk loads. */}
      <Suspense fallback={<LoadingState />}>
        <Screen route={route} session={auth.session} onSessionChanged={updateSession} />
      </Suspense>
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
      return DashboardPreviewPage === null ? <EmptyState title="Página não encontrada." /> : <DashboardPreviewPage />;
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
