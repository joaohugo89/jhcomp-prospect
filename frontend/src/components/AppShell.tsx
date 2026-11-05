import {
  Ban,
  Building2,
  Columns3,
  FileUp,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { useState } from 'react';

import { Link, type Route } from '../app/router';
import type { Session } from '../types/session';

interface AppShellProps {
  readonly session: Session;
  readonly route: Route;
  readonly onSignOut: () => Promise<void>;
  readonly children: React.ReactNode;
}

interface NavItem {
  readonly to: string;
  readonly label: string;
  readonly icon: LucideIcon;
  readonly active: (route: Route) => boolean;
  readonly adminOnly?: boolean;
}

/**
 * Itens de navegação (doc 08 §3), em grupos: o trabalho do dia, a base de
 * contatos e a administração. Só entram os itens já implementados.
 */
const NAV_GROUPS: readonly (readonly [string, readonly NavItem[]])[] = [
  [
    'Trabalho',
    [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, active: (route) => route.name === 'dashboard' },
      { to: '/pipeline', label: 'Pipeline', icon: Columns3, active: (route) => route.name === 'pipeline' },
      {
        to: '/leads',
        label: 'Leads',
        icon: ListChecks,
        active: (route) => route.name === 'leads' || route.name === 'lead',
      },
      {
        to: '/companies',
        label: 'Empresas',
        icon: Building2,
        active: (route) => route.name === 'companies' || route.name === 'company' || route.name === 'companyNew',
      },
    ],
  ],
  [
    'Base',
    [
      {
        to: '/imports',
        label: 'Importações',
        icon: FileUp,
        active: (route) => route.name === 'imports' || route.name === 'import',
      },
      { to: '/do-not-contact', label: 'Não contato', icon: Ban, active: (route) => route.name === 'suppression' },
    ],
  ],
  [
    'Administração',
    [
      {
        to: '/settings/members',
        label: 'Membros',
        icon: Users,
        active: (route) => route.name === 'members',
        adminOnly: true,
      },
    ],
  ],
];

const ROLE_LABEL = { ADMIN: 'Administrador', OPERATOR: 'Operador' } as const;

/**
 * Telas que ocupam a tela inteira a partir do tablet: sem limite de largura e
 * com a altura da janela, para o conteúdo rolar por dentro (colunas do Kanban).
 */
const FULL_SCREEN: ReadonlySet<Route['name']> = new Set(['pipeline']);

/** Formulários de uma coluna: largura de leitura, para a linha não esticar. */
const READING: ReadonlySet<Route['name']> = new Set(['password']);

function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return `${parts[0]?.[0] ?? '?'}${parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : ''}`.toUpperCase();
}

/**
 * Estrutura das telas autenticadas. No desktop, barra lateral fixa com a
 * navegação e a pessoa no rodapé: sem barra superior, o conteúdo começa no
 * topo da janela. No celular, a mesma barra vira a faixa de cima.
 */
export function AppShell({ session, route, onSignOut, children }: AppShellProps): React.JSX.Element {
  const [signingOut, setSigningOut] = useState(false);
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
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:shadow"
      >
        Ir para o conteúdo
      </a>

      <aside className="border-b border-slate-200 bg-white md:sticky md:top-0 md:flex md:h-dvh md:w-56 md:shrink-0 md:flex-col md:border-r md:border-b-0">
        <div className="flex items-center gap-2 px-4 pt-3 pb-2 md:pt-4">
          <span
            aria-hidden="true"
            className="inline-flex size-7 items-center justify-center rounded-lg bg-brand-600 text-[11px] font-bold text-white"
          >
            JH
          </span>
          <p className="text-sm font-bold tracking-tight">JH.COMP Prospect</p>
        </div>

        <nav aria-label="Principal" className="px-2 md:flex-1 md:overflow-y-auto md:py-2">
          {/* Em tela estreita os links viram uma faixa que rola sozinha: a página nunca rola de lado.
              O p-1 evita que o overflow corte o contorno de foco. */}
          <ul className="flex gap-1 overflow-x-auto p-1 whitespace-nowrap md:flex-col md:gap-4 md:overflow-visible">
            {NAV_GROUPS.map(([group, all]) => {
              const items = all.filter((item) => item.adminOnly !== true || session.role === 'ADMIN');
              if (items.length === 0) {
                return null;
              }
              return (
                <li key={group} className="contents md:block">
                  <p className="hidden px-2 pb-1 text-[10px] font-semibold tracking-wider text-slate-400 uppercase md:block">
                    {group}
                  </p>
                  <ul className="contents md:flex md:flex-col md:gap-0.5">
                    {items.map((item) => {
                      const active = item.active(route);
                      const Icon = item.icon;
                      return (
                        <li key={item.to}>
                          <Link
                            to={item.to}
                            {...(active ? { 'aria-current': 'page' as const } : {})}
                            className={`flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
                              active
                                ? 'bg-brand-50 text-brand-700'
                                : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                            }`}
                          >
                            <Icon aria-hidden="true" className={`size-4 shrink-0 ${active ? '' : 'text-slate-400'}`} />
                            {item.label}
                          </Link>
                        </li>
                      );
                    })}
                  </ul>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="flex items-center gap-2 border-t border-slate-100 px-3 py-2 md:py-3">
          <span
            aria-hidden="true"
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-bold text-slate-600"
          >
            {initials(session.displayName)}
          </span>
          <div className="min-w-0 flex-1 leading-tight">
            <p className="truncate text-xs font-semibold text-slate-800">{session.displayName}</p>
            <p className="truncate text-[11px] text-slate-500">{ROLE_LABEL[session.role]}</p>
          </div>
          <Link
            to="/settings/password"
            aria-label="Minha senha"
            title="Minha senha"
            {...(route.name === 'password' ? { 'aria-current': 'page' as const } : {})}
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500"
          >
            <KeyRound aria-hidden="true" className="size-4" />
          </Link>
          <button
            type="button"
            aria-label="Sair"
            title="Sair"
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500 disabled:opacity-50"
            disabled={signingOut}
            onClick={() => {
              void handleSignOut();
            }}
          >
            <LogOut aria-hidden="true" className="size-4" />
          </button>
        </div>
      </aside>

      <main
        id="conteudo"
        tabIndex={-1}
        className={`min-w-0 flex-1 px-4 py-4 focus:outline-none md:px-6 md:py-5 ${
          fullScreen
            ? 'md:flex md:min-h-0 md:flex-col'
            : `mx-auto w-full ${READING.has(route.name) ? 'max-w-4xl' : 'max-w-[100rem]'}`
        }`}
      >
        {children}
      </main>
    </div>
  );
}
