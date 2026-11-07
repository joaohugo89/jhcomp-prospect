import { afterEach, describe, expect, it, vi } from 'vitest';

import { HttpError, onSessionExpired, request, toQuery } from './httpClient';

type FetchMock = ReturnType<typeof vi.fn<(url: string, init?: RequestInit) => Promise<Response>>>;

function stubFetch(response: Response): FetchMock {
  const spy = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(() => Promise.resolve(response));
  vi.stubGlobal('fetch', spy);
  return spy;
}

/** The headers sent on the first call. */
function sentHeaders(spy: FetchMock): Record<string, string> {
  const init = spy.mock.calls[0]?.[1];
  if (init === undefined) {
    throw new Error('fetch não foi chamado com opções');
  }
  return init.headers as Record<string, string>;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** The centralised HTTP client: cookie session and CSRF token (decision D1). */
describe('httpClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    // Clears the cookie between tests so no state leaks.
    document.cookie = 'JH_XSRF=; Max-Age=0; path=/';
  });

  it('envia o cookie de sessao em toda requisicao', async () => {
    const spy = stubFetch(jsonResponse({ ok: true }));

    await request('/health');

    expect(spy.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' });
  });

  it('envia o token CSRF em POST quando o cookie existe', async () => {
    document.cookie = 'JH_XSRF=token-de-teste; path=/';
    const spy = stubFetch(jsonResponse({ ok: true }));

    await request('/auth/login', { method: 'POST', body: { email: 'a@b.test' } });

    const headers = sentHeaders(spy);
    expect(headers['X-JH-XSRF']).toBe('token-de-teste');
  });

  it('nao envia o token CSRF em GET', async () => {
    document.cookie = 'JH_XSRF=token-de-teste; path=/';
    const spy = stubFetch(jsonResponse({ ok: true }));

    await request('/health');

    const headers = sentHeaders(spy);
    expect(headers['X-JH-XSRF']).toBeUndefined();
  });

  it('converte o erro da API em HttpError com os campos do contrato', async () => {
    stubFetch(
      jsonResponse(
        {
          type: 'VALIDATION_ERROR',
          title: 'Request validation failed',
          status: 400,
          detail: 'One or more fields are invalid',
          errors: [{ field: 'email', message: 'must be a valid email' }],
        },
        400,
      ),
    );

    const error = await request('/auth/login', { method: 'POST', body: {} }).catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(HttpError);
    const httpError = error as HttpError;
    expect(httpError.status).toBe(400);
    expect(httpError.type).toBe('VALIDATION_ERROR');
    expect(httpError.fieldErrors.get('email')).toBe('must be a valid email');
  });

  it('nao quebra quando o erro nao tem corpo JSON', async () => {
    stubFetch(new Response('', { status: 502, statusText: 'Bad Gateway' }));

    const error = (await request('/health').catch((cause: unknown) => cause)) as HttpError;

    expect(error.status).toBe(502);
    expect(error.type).toBe('UNEXPECTED_ERROR');
  });

  it('avisa sessao expirada em 401 fora de /auth, e nao no login', async () => {
    const listener = vi.fn<() => void>();
    const unsubscribe = onSessionExpired(listener);

    stubFetch(jsonResponse({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401));
    await request('/auth/login', { method: 'POST', body: {} }).catch(() => undefined);
    expect(listener).not.toHaveBeenCalled();

    stubFetch(jsonResponse({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401));
    await request('/leads').catch(() => undefined);
    expect(listener).toHaveBeenCalledTimes(1);

    unsubscribe();
    stubFetch(jsonResponse({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401));
    await request('/leads').catch(() => undefined);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('monta query string sem valores vazios', () => {
    expect(toQuery({ search: 'salão & cia', city: '', archived: false, page: 0, x: undefined })).toBe(
      '?search=sal%C3%A3o+%26+cia&page=0',
    );
    expect(toQuery({ city: '' })).toBe('');
  });
});
