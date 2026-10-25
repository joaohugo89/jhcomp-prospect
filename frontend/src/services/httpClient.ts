/**
 * Cliente HTTP centralizado (doc 03 §2 e §10).
 *
 * Toda chamada à API passa por aqui. Nenhum `fetch` solto nos módulos: é o que
 * garante um único lugar para credenciais de sessão, tratamento de erro e, na
 * Etapa 3, o token CSRF e o tratamento de sessão expirada.
 */
import type { ApiError, ProblemDetail } from '../types/apiError';

// Caminho relativo: frontend e API ficam na mesma origem, servidos pelo nginx
// em container e pelo proxy do Vite em desenvolvimento (decisao D1).
const DEFAULT_BASE_URL = '/api/v1';
const DEFAULT_TIMEOUT_MS = 15_000;
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const UNSAFE_METHODS = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

export const apiBaseUrl: string = import.meta.env.VITE_API_BASE_URL ?? DEFAULT_BASE_URL;

/** Erro de API já normalizado, sempre lançado por este módulo. */
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
 * Le o token CSRF que o backend emite em cookie (decisao D1).
 *
 * Este cookie e deliberadamente legivel por JavaScript, ao contrario do cookie
 * de sessao: o frontend precisa reenviar o valor em cabecalho. A protecao
 * continua valendo porque um site atacante nao consegue ler cookies de outra
 * origem.
 */
function readCsrfToken(): string | undefined {
  // document pode nao existir em ambiente sem DOM.
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
    // Resposta sem corpo JSON: não é motivo para esconder o status.
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

  // RNF-005: toda chamada externa tem timeout.
  const timeoutSignal = AbortSignal.timeout(timeoutMs);
  const combinedSignal = signal ? AbortSignal.any([signal, timeoutSignal]) : timeoutSignal;

  // Metodos que alteram estado exigem o token CSRF; GET nao.
  const csrfToken = UNSAFE_METHODS.has(method) ? readCsrfToken() : undefined;

  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    // A sessão é um cookie HttpOnly (decisão D1), por isso credentials é
    // obrigatório e nenhum token é guardado em JavaScript.
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(csrfToken === undefined ? {} : { [CSRF_HEADER]: csrfToken }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: combinedSignal,
  });

  if (!response.ok) {
    throw await toHttpError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
