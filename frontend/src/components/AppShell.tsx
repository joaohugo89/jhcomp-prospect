import {
  Ban,
  Building2,
  Columns3,
  FileUp,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Moon,
  Sun,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { useState } from 'react';

import { initials } from '../app/format';
import { Link, type Route } from '../app/router';
import { useTheme } from '../app/theme';
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
 * Navigation items (doc 08 §3), in groups: the day's work, the contact base
 * and administration. Only items that are actually implemented appear.
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
 * Screens that fill the viewport from tablet up: no width cap and the height
 * of the window, so the content scrolls inside it (the Kanban columns).
 */
const FULL_SCREEN: ReadonlySet<Route['name']> = new Set(['pipeline']);

/** Single-column forms: reading width, so a line does not stretch. */
const READING: ReadonlySet<Route['name']> = new Set(['password']);

/**
 * The shell of every authenticated screen. On desktop a fixed sidebar holds
 * the navigation, with the signed-in person at the bottom: with no top bar the
 * content starts at the top of the window. On mobile the same bar becomes the
 * strip across the top.
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
    <div className={`min-h-dvh text-stone-900 md:flex ${fullScreen ? 'md:h-dvh md:overflow-hidden' : ''}`}>
      <a
        href="#conteudo"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:glass focus:px-3 focus:py-2 focus:shadow"
      >
        Ir para o conteúdo
      </a>

      <aside className="border-b border-stone-200 nav-surface text-cream md:sticky md:top-0 md:flex md:h-dvh md:w-56 md:shrink-0 md:flex-col md:border-r md:border-b-0 md:border-stone-200">
        <div className="flex items-center gap-1.5 px-3 pt-3 pb-2 md:pt-4">
          <span
            aria-hidden="true"
            className="inline-flex size-7 items-center justify-center rounded-lg bg-brand-500 font-mono text-[11px] font-bold text-white"
          >
            JH
          </span>
          <p className="font-display text-[15px] leading-none font-semibold tracking-tight whitespace-nowrap">
            JH.COMP <em className="font-normal text-brand-200">Prospect</em>
          </p>
          <ThemeToggle />
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
                  <p className="hidden px-2 pb-1 font-mono text-[10px] font-semibold tracking-wider text-cream/40 uppercase md:block">
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
                            className={`flex items-center gap-2 rounded-lg border-l-2 px-2.5 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-cream ${
                              active
                                ? 'border-brand-500 bg-oxblood-lift text-white'
                                : 'border-transparent text-cream/70 hover:bg-oxblood-lift hover:text-white'
                            }`}
                          >
                            <Icon
                              aria-hidden="true"
                              className={`size-4 shrink-0 ${active ? 'text-brand-200' : 'text-cream/45'}`}
                            />
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

        <div className="flex items-center gap-2 border-t border-white/10 px-3 py-2 md:py-3">
          <span
            aria-hidden="true"
            className="inline-flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-500 text-xs font-bold text-cream"
          >
            {initials(session.displayName)}
          </span>
          <div className="min-w-0 flex-1 leading-tight">
            <p className="truncate text-xs font-semibold text-cream">{session.displayName}</p>
            <p className="truncate text-[11px] text-cream/55">{ROLE_LABEL[session.role]}</p>
          </div>
          <Link
            to="/settings/password"
            aria-label="Minha senha"
            title="Minha senha"
            {...(route.name === 'password' ? { 'aria-current': 'page' as const } : {})}
            className="rounded-lg p-1.5 text-cream/60 hover:bg-oxblood-lift hover:text-white focus-visible:outline-2 focus-visible:outline-cream"
          >
            <KeyRound aria-hidden="true" className="size-4" />
          </Link>
          <button
            type="button"
            aria-label="Sair"
            title="Sair"
            className="rounded-lg p-1.5 text-cream/60 hover:bg-oxblood-lift hover:text-white focus-visible:outline-2 focus-visible:outline-cream disabled:opacity-50"
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

/** Toggles light and dark. The state is in the accessible label, not only in the icon. */
function ThemeToggle(): React.ReactElement {
  const [theme, setTheme] = useTheme();
  const dark = theme === 'DARK';
  const Icon = dark ? Sun : Moon;

  return (
    <button
      type="button"
      aria-label={dark ? 'Tema escuro. Mudar para o claro' : 'Tema claro. Mudar para o escuro'}
      title={dark ? 'Tema escuro' : 'Tema claro'}
      className="ml-auto rounded-lg p-1 text-cream/60 hover:bg-oxblood-lift hover:text-white focus-visible:outline-2 focus-visible:outline-cream"
      onClick={() => {
        setTheme(dark ? 'LIGHT' : 'DARK');
      }}
    >
      <Icon aria-hidden="true" className="size-4" />
    </button>
  );
}
