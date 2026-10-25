import { afterEach, describe, expect, it, vi } from 'vitest';

import { HttpError, request } from './httpClient';

/** Cliente HTTP centralizado: sessao por cookie e token CSRF (decisao D1). */
describe('httpClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    // Limpa o cookie entre testes para nao vazar estado.
    document.cookie = 'JH_XSRF=; Max-Age=0; path=/';
  });

  function stubFetch(response: Response): ReturnType<typeof vi.fn> {
    const spy = vi.fn(() => Promise.resolve(response));
    vi.stubGlobal('fetch', spy);
    return spy;
  }

  function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  it('envia o cookie de sessao em toda requisicao', async () => {
    const spy = stubFetch(jsonResponse({ ok: true }));

    await request('/health');

    expect(spy.mock.calls[0]?.[1]).toMatchObject({ credentials: 'include' });
  });

  it('envia o token CSRF em POST quando o cookie existe', async () => {
    document.cookie = 'JH_XSRF=token-de-teste; path=/';
    const spy = stubFetch(jsonResponse({ ok: true }));

    await request('/auth/login', { method: 'POST', body: { email: 'a@b.test' } });

    const headers = (spy.mock.calls[0]?.[1] as RequestInit).headers as Record<string, string>;
    expect(headers['X-JH-XSRF']).toBe('token-de-teste');
  });

  it('nao envia o token CSRF em GET', async () => {
    document.cookie = 'JH_XSRF=token-de-teste; path=/';
    const spy = stubFetch(jsonResponse({ ok: true }));

    await request('/health');

    const headers = (spy.mock.calls[0]?.[1] as RequestInit).headers as Record<string, string>;
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

    const error = await request('/auth/login', { method: 'POST', body: {} }).catch(
      (cause: unknown) => cause,
    );

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
});
