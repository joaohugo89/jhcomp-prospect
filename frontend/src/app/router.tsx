import { useSyncExternalStore } from 'react';

/**
 * Minimal routing over the History API.
 *
 * There are few screens and no nested routes: a routing library would be a
 * dependency with no use to justify it. nginx already serves `index.html` for
 * any unknown path, so the addresses are real and can be opened directly.
 */
export type Route =
  | { readonly name: 'dashboard' }
  /** Development only: the dashboard layout running on mock data. */
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

/** Canonical UUID; an id outside the format never reaches the API. */
const ID = '([0-9a-fA-F-]{36})';

type Pattern = [RegExp, (match: RegExpMatchArray) => Route];

/** In a production build `DEV` is `false`, so the mock preview does not exist at all. */
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

/** Path plus query string: changing only the filters in the URL is navigation too. */
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
  /** Accessible name when the visible text is not enough (an icon, or extra context). */
  readonly 'aria-label'?: string;
  /** Hover hint, for an icon-only link. */
  readonly title?: string;
}

/**
 * Internal link: a real `<a>`, so it opens in a new tab with Ctrl/⌘ or the
 * middle button and works with the keyboard at no extra cost.
 */
export function Link({ to, className, children, ...rest }: LinkProps): React.JSX.Element {
  function handleClick(event: React.MouseEvent<HTMLAnchorElement>): void {
    // An anchor on the same page (`#atencao`): the browser scrolls by itself.
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

/**
 * Writes the query string into the URL without navigating: back and reload
 * keep the filters, and the history does not gain one entry per filter.
 */
export function replaceQuery(query: string): void {
  window.history.replaceState(null, '', `${window.location.pathname}${query === '' ? '' : `?${query}`}`);
}
