/**
 * Centralised HTTP client (doc 03 §2 and §10).
 *
 * Every API call goes through here. No loose `fetch` in the modules: that is
 * what keeps the session cookie, the CSRF token, timeouts, the error shape and
 * the session-expired notice in a single place.
 */
import type { ApiError, ProblemDetail } from '../types/apiError';

// A relative path: the frontend and the API share one origin, served by nginx
// in containers and by the Vite proxy in development (decision D1).
const DEFAULT_BASE_URL = '/api/v1';
const DEFAULT_TIMEOUT_MS = 15_000;
// Project-specific names, defined in SecurityConfig. Cookies are not isolated
// by port: a generic name would collide with another project on localhost, the
// browser would send two cookies with the same name, and the server would
// compare the header against the wrong value and refuse with 403.
const CSRF_COOKIE = 'JH_XSRF';
const CSRF_HEADER = 'X-JH-XSRF';
const UNSAFE_METHODS = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

const sessionExpiredListeners = new Set<() => void>();

const apiBaseUrl: string = import.meta.env.VITE_API_BASE_URL ?? DEFAULT_BASE_URL;

/** An already normalised API error, the only thing this module throws. */
export class HttpError extends Error implements ApiError {
  readonly status: number;
  readonly type: string;
  readonly detail: string | undefined;
  readonly fieldErrors: ReadonlyMap<string, string>;

  constructor(status: number, type: string, title: string, detail?: string, fieldErrors?: ReadonlyMap<string, string>) {
    super(title);
    this.name = 'HttpError';
    this.status = status;
    this.type = type;
    this.detail = detail;
    this.fieldErrors = fieldErrors ?? new Map();
  }
}

/**
 * Reads the CSRF token the backend issues in a cookie (decision D1).
 *
 * This cookie is deliberately readable by JavaScript, unlike the session
 * cookie: the frontend has to echo the value in a header. The protection still
 * holds because an attacking site cannot read cookies from another origin.
 */
function readCsrfToken(): string | undefined {
  // `document` may not exist in an environment without a DOM.
  if (typeof document === 'undefined') {
    return undefined;
  }

  for (const entry of document.cookie.split(';')) {
    const separator = entry.indexOf('=');
    if (separator < 0) {
      continue;
    }
    if (entry.slice(0, separator).trim() === CSRF_COOKIE) {
      return decodeURIComponent(entry.slice(separator + 1).trim());
    }
  }

  return undefined;
}

interface RequestOptions {
  readonly method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  readonly body?: unknown;
  readonly signal?: AbortSignal;
  readonly timeoutMs?: number;
}

function isProblemDetail(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null && 'status' in value;
}

async function toHttpError(response: Response): Promise<HttpError> {
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    // A response with no JSON body is no reason to hide the status.
    payload = undefined;
  }

  if (!isProblemDetail(payload)) {
    return new HttpError(response.status, 'UNEXPECTED_ERROR', response.statusText || 'Erro inesperado');
  }

  const fieldErrors = new Map<string, string>();
  for (const item of payload.errors ?? []) {
    fieldErrors.set(item.field, item.message);
  }

  return new HttpError(
    payload.status ?? response.status,
    payload.type ?? 'UNEXPECTED_ERROR',
    payload.title ?? response.statusText,
    payload.detail,
    fieldErrors,
  );
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, signal, timeoutMs = DEFAULT_TIMEOUT_MS } = options;

  // RNF-005: every external call has a timeout.
  const timeoutSignal = AbortSignal.timeout(timeoutMs);
  const combinedSignal = signal ? AbortSignal.any([signal, timeoutSignal]) : timeoutSignal;

  // State-changing methods require the CSRF token; GET does not.
  const csrfToken = UNSAFE_METHODS.has(method) ? readCsrfToken() : undefined;

  // FormData goes as multipart: the browser sets the Content-Type with the
  // boundary, which cannot be written by hand.
  const isForm = body instanceof FormData;

  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    // The session is an HttpOnly cookie (decision D1), so credentials are
    // required and no token is ever held in JavaScript.
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body === undefined || isForm ? {} : { 'Content-Type': 'application/json' }),
      ...(csrfToken === undefined ? {} : { [CSRF_HEADER]: csrfToken }),
    },
    ...(body === undefined ? {} : { body: isForm ? body : JSON.stringify(body) }),
    signal: combinedSignal,
  });

  if (!response.ok) {
    // A 401 outside /auth means the session the screen assumed no longer
    // exists. The /auth routes handle their own 401: on login it is an invalid
    // credential, on /auth/me it simply means "not authenticated".
    if (response.status === 401 && !path.startsWith('/auth/')) {
      for (const listener of sessionExpiredListeners) {
        listener();
      }
    }
    throw await toHttpError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

/**
 * Notifies when the API reports that the session has expired (doc 08 §11).
 *
 * One place, rather than every screen handling 401: the answer is always the
 * same — go back to login and explain why.
 *
 * @returns a function that cancels the subscription
 */
export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

/** Builds the query string, omitting empty values so no inert filter is sent. */
export function toQuery(params: Readonly<Record<string, string | number | boolean | undefined>>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '' || value === false) {
      continue;
    }
    search.set(key, String(value));
  }
  const text = search.toString();
  return text === '' ? '' : `?${text}`;
}
