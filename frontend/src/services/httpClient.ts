/**
 * Cliente HTTP centralizado (doc 03 §2 e §10).
 *
 * Toda chamada à API passa por aqui. Nenhum `fetch` solto nos módulos: é o que
 * garante um único lugar para cookie de sessão, token CSRF, timeout, formato de
 * erro e aviso de sessão expirada.
 */
import type { ApiError, ProblemDetail } from '../types/apiError';

// Caminho relativo: frontend e API ficam na mesma origem, servidos pelo nginx
// em container e pelo proxy do Vite em desenvolvimento (decisao D1).
const DEFAULT_BASE_URL = '/api/v1';
const DEFAULT_TIMEOUT_MS = 15_000;
// Nome proprio do projeto, definido em SecurityConfig. Cookie nao e isolado
// por porta: um nome generico colidiria com outro projeto em localhost e o
// navegador enviaria dois cookies de mesmo nome, levando o servidor a comparar
// o cabecalho contra o valor errado e recusar com 403.
const CSRF_COOKIE = 'JH_XSRF';
const CSRF_HEADER = 'X-JH-XSRF';
const UNSAFE_METHODS = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

const sessionExpiredListeners = new Set<() => void>();

const apiBaseUrl: string = import.meta.env.VITE_API_BASE_URL ?? DEFAULT_BASE_URL;

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

  // FormData vai como multipart: o navegador define o Content-Type com o
  // boundary, que não pode ser escrito à mão.
  const isForm = body instanceof FormData;

  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    // A sessão é um cookie HttpOnly (decisão D1), por isso credentials é
    // obrigatório e nenhum token é guardado em JavaScript.
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
    // 401 fora de /auth significa que a sessão que a tela pressupunha não
    // existe mais. As rotas de /auth tratam o 401 elas mesmas: no login é
    // credencial inválida, em /auth/me é apenas "não autenticado".
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
 * Avisa quando a API responde que a sessão expirou (doc 08 §11).
 *
 * Um único ponto, em vez de cada tela tratar o 401: a resposta é sempre a
 * mesma, voltar ao login explicando o motivo.
 *
 * @returns função que cancela a inscrição
 */
export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

/** Monta a query string omitindo valores vazios, para não enviar filtro sem efeito. */
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
