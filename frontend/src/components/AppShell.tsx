import { useState } from 'react';

import { Link, type Route } from '../app/router';
import type { Session } from '../types/session';
import { buttonSecondary, link } from './styles';

interface AppShellProps {
  readonly session: Session;
  readonly route: Route;
  readonly onSignOut: () => Promise<void>;
  readonly children: React.ReactNode;
}

interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly active: (route: Route) => boolean;
  readonly adminOnly?: boolean;
}

/**
 * Itens de navegação (doc 08 §3).
 *
 * Só entram os itens já implementados. Pesquisa de empresas e Outreach
 * chegam nas etapas seguintes; mostrá-los agora seria simular funcionalidade,
 * o que o doc 08 proíbe.
 */
const NAV_ITEMS: readonly NavItem[] = [
  { to: '/dashboard', label: 'Dashboard', active: (route) => route.name === 'dashboard' },
  { to: '/pipeline', label: 'Pipeline', active: (route) => route.name === 'pipeline' },
  { to: '/leads', label: 'Leads', active: (route) => route.name === 'leads' || route.name === 'lead' },
  {
    to: '/companies',
    label: 'Empresas',
    active: (route) => route.name === 'companies' || route.name === 'company' || route.name === 'companyNew',
  },
  {
    to: '/imports',
    label: 'Importações',
    active: (route) => route.name === 'imports' || route.name === 'import',
  },
  { to: '/do-not-contact', label: 'Não contato', active: (route) => route.name === 'suppression' },
  { to: '/settings/members', label: 'Membros', active: (route) => route.name === 'members', adminOnly: true },
];

const ROLE_LABEL = { ADMIN: 'Administrador', OPERATOR: 'Operador' } as const;

/**
 * Telas que ocupam a tela inteira a partir do tablet: sem limite de largura e
 * com a altura da janela, para o conteúdo rolar por dentro (colunas do Kanban).
 * As demais mantêm a largura de leitura e a rolagem da página.
 */
const FULL_SCREEN: ReadonlySet<Route['name']> = new Set(['pipeline']);

/** Estrutura das telas autenticadas: barra lateral, cabeçalho e conteúdo. */
export function AppShell({ session, route, onSignOut, children }: AppShellProps): React.JSX.Element {
  const [signingOut, setSigningOut] = useState(false);
  const items = NAV_ITEMS.filter((item) => item.adminOnly !== true || session.role === 'ADMIN');
  const fullScreen = FULL_SCREEN.has(route.name);

  async function handleSignOut(): Promise<void> {
    setSigningOut(true);
    try {
      await onSignOut();
    } finally {
      setSigningOut(false);
    }
  }

  return (
    <div className={`min-h-dvh bg-slate-50 text-slate-900 md:flex ${fullScreen ? 'md:h-dvh md:overflow-hidden' : ''}`}>
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:shadow"
      >
        Ir para o conteúdo
      </a>

      <aside className="border-b border-slate-200 bg-white md:w-56 md:shrink-0 md:border-r md:border-b-0">
        <div className="px-4 py-4">
          <p className="text-base font-semibold tracking-tight">JH.COMP Prospect</p>
        </div>
        <nav aria-label="Principal" className="px-2 pb-3">
          {/* Em tela estreita a faixa de links rola sozinha: a página nunca rola de lado.
              O p-1 evita que o overflow corte o contorno de foco. */}
          <ul className="flex gap-1 overflow-x-auto p-1 whitespace-nowrap md:flex-col md:overflow-visible">
            {items.map((item) => {
              const active = item.active(route);
              return (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    {...(active ? { 'aria-current': 'page' as const } : {})}
                    className={`block rounded-md px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
                      active ? 'bg-brand-50 text-brand-700' : 'text-slate-700 hover:bg-slate-100'
                    }`}
                  >
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      </aside>

      <div className={`min-w-0 flex-1 ${fullScreen ? 'md:flex md:min-h-0 md:flex-col' : ''}`}>
        <header className="flex flex-wrap items-center justify-end gap-3 border-b border-slate-200 bg-white px-4 py-2 text-sm">
          <span className="text-slate-700">
            {session.displayName} · <span className="text-slate-500">{ROLE_LABEL[session.role]}</span>
          </span>
          <Link
            to="/settings/password"
            className={link}
            {...(route.name === 'password' ? { 'aria-current': 'page' as const } : {})}
          >
            Minha senha
          </Link>
          <button
            type="button"
            className={buttonSecondary}
            disabled={signingOut}
            onClick={() => {
              void handleSignOut();
            }}
          >
            Sair
          </button>
        </header>
        <main
          id="conteudo"
          tabIndex={-1}
          className={`px-4 py-6 focus:outline-none ${
            fullScreen ? 'md:flex md:min-h-0 md:flex-1 md:flex-col md:py-4' : 'mx-auto max-w-6xl'
          }`}
        >
          {children}
        </main>
      </div>
    </div>
  );
}
