import { useSyncExternalStore } from 'react';

/**
 * Roteamento mínimo por History API.
 *
 * São poucas telas e nenhuma rota aninhada: uma biblioteca de roteamento
 * seria dependência sem uso que a justifique. O nginx já devolve o
 * `index.html` para qualquer caminho desconhecido, então os endereços são
 * reais e podem ser abertos diretamente.
 */
export type Route =
  | { readonly name: 'dashboard' }
  /** Só em desenvolvimento: layout do dashboard com dados simulados. */
  | { readonly name: 'dashboardPreview' }
  | { readonly name: 'pipeline' }
  | { readonly name: 'companies' }
  | { readonly name: 'companyNew' }
  | { readonly name: 'company'; readonly id: string }
  | { readonly name: 'leads' }
  | { readonly name: 'lead'; readonly id: string }
  | { readonly name: 'imports' }
  | { readonly name: 'import'; readonly id: string }
  | { readonly name: 'members' }
  | { readonly name: 'suppression' }
  | { readonly name: 'password' }
  | { readonly name: 'notFound' };

const NAVIGATION_EVENT = 'jh:navigate';

/** UUID canônico; um id fora do formato nem chega à API. */
const ID = '([0-9a-fA-F-]{36})';

type Pattern = [RegExp, (match: RegExpMatchArray) => Route];

/** No build de produção `DEV` é `false`: a pré-visualização com dados simulados nem existe. */
const DEV_PATTERNS: readonly Pattern[] = import.meta.env.DEV
  ? [[/^\/dashboard\/preview\/?$/, () => ({ name: 'dashboardPreview' })]]
  : [];

const PATTERNS: readonly Pattern[] = [
  [/^\/?$/, () => ({ name: 'dashboard' })],
  [/^\/dashboard\/?$/, () => ({ name: 'dashboard' })],
  ...DEV_PATTERNS,
  [/^\/pipeline\/?$/, () => ({ name: 'pipeline' })],
  [/^\/leads\/?$/, () => ({ name: 'leads' })],
  [new RegExp(`^/leads/${ID}/?$`), (match) => ({ name: 'lead', id: match[1] ?? '' })],
  [/^\/companies\/?$/, () => ({ name: 'companies' })],
  [/^\/companies\/new\/?$/, () => ({ name: 'companyNew' })],
  [new RegExp(`^/companies/${ID}/?$`), (match) => ({ name: 'company', id: match[1] ?? '' })],
  [/^\/imports\/?$/, () => ({ name: 'imports' })],
  [new RegExp(`^/imports/${ID}/?$`), (match) => ({ name: 'import', id: match[1] ?? '' })],
  [/^\/settings\/members\/?$/, () => ({ name: 'members' })],
  [/^\/do-not-contact\/?$/, () => ({ name: 'suppression' })],
  [/^\/settings\/password\/?$/, () => ({ name: 'password' })],
];

export function parseRoute(pathname: string): Route {
  for (const [pattern, toRoute] of PATTERNS) {
    const match = pathname.match(pattern);
    if (match) {
      return toRoute(match);
    }
  }
  return { name: 'notFound' };
}

export function navigate(path: string): void {
  window.history.pushState(null, '', path);
  window.dispatchEvent(new Event(NAVIGATION_EVENT));
}

function subscribe(onChange: () => void): () => void {
  window.addEventListener('popstate', onChange);
  window.addEventListener(NAVIGATION_EVENT, onChange);
  return () => {
    window.removeEventListener('popstate', onChange);
    window.removeEventListener(NAVIGATION_EVENT, onChange);
  };
}

/** Caminho com a query string: trocar só os filtros da URL também é navegação. */
function currentLocation(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function useRoute(): Route {
  const location = useSyncExternalStore(subscribe, currentLocation);
  return parseRoute(location.split('?')[0] ?? '');
}

interface LinkProps {
  readonly to: string;
  readonly className?: string;
  readonly children: React.ReactNode;
  readonly 'aria-current'?: 'page';
  /** Nome acessível quando o texto visível não basta (ícone, ou contexto extra). */
  readonly 'aria-label'?: string;
  /** Dica ao passar o mouse, para link só com ícone. */
  readonly title?: string;
}

/**
 * Link interno: é um `<a>` de verdade, então abre em nova aba com Ctrl/⌘ ou
 * botão do meio e funciona com teclado sem esforço extra.
 */
export function Link({ to, className, children, ...rest }: LinkProps): React.JSX.Element {
  function handleClick(event: React.MouseEvent<HTMLAnchorElement>): void {
    // Âncora na mesma página (`#atencao`): o navegador rola sozinho.
    if (
      to.startsWith('#') ||
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return;
    }
    event.preventDefault();
    navigate(to);
  }

  return (
    <a href={to} onClick={handleClick} className={className} {...rest}>
      {children}
    </a>
  );
}
