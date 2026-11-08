import { lazy, Suspense, useEffect, useState } from 'react';

import { Link, useRoute, type Route } from './app/router';
import { AppShell } from './components/AppShell';
import { EmptyState, LoadingState } from './components/States';
import { buttonPrimary } from './components/styles';
import { LoginPage } from './modules/auth/LoginPage';
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

/*
 * Both exports of PasswordPages are lazy, and deliberately so: importing one of
 * them eagerly would keep the module in the initial chunk and silently cancel
 * the split of the other. Neither is on the critical path - a normal session
 * has no forced password change.
 */
const ForcedPasswordChange = lazyPage(
  async () => (await import('./modules/auth/PasswordPages')).ForcedPasswordChange,
);

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

/** The application root: session, routing and the shared shell. */
export function App(): React.JSX.Element {
  const [auth, setAuth] = useState<AuthState>({ kind: 'checking' });
  const route = useRoute();

  // The session is verified on the server, not from anything stored in the
  // browser: the cookie is HttpOnly and the client cannot inspect it. This GET
  // also makes the backend issue the CSRF token cookie, which the login POST
  // depends on.
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

  // A session revoked or expired mid-use (D21): return to login with the
  // reason, instead of every screen showing a generic error.
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
      // Even if the call fails the interface returns to login: insisting on
      // showing a session that may no longer exist would be worse.
      setAuth({ kind: 'anonymous', expired: false });
    }
  }

  function updateSession(session: Session): void {
    setAuth({ kind: 'authenticated', session });
  }

  if (auth.session.passwordChangeRequired) {
    return (
      <Suspense fallback={<LoadingState />}>
        <ForcedPasswordChange
          session={auth.session}
          onChanged={updateSession}
          onSignOut={() => {
            void signOut();
          }}
        />
      </Suspense>
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
      // key: a different filter in the URL (a dashboard drill-down) is a different list.
      return <LeadsPage key={window.location.search} session={session} />;
    case 'lead':
      // key: switching lead recreates the screen, inheriting no state from the previous one.
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
