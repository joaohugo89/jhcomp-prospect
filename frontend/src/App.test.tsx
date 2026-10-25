import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';

/**
 * Cobre os estados de interface exigidos pelo doc 08 §11 e o fluxo de
 * autenticação da Etapa 3. O `fetch` é substituído por mock: teste de frontend
 * não depende de rede nem do backend.
 */
describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function json(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  const health = { status: 'UP', version: '0.1.0-TEST', checkedAt: '2026-01-15T12:00:00Z' };

  const session = {
    userId: 'u-1',
    email: 'admin@jhcomp.local',
    displayName: 'Administrador',
    organizationId: 'org-1',
    role: 'ADMIN' as const,
  };

  /** Responde por rota, para que cada teste descreva só o que lhe interessa. */
  function stubRoutes(routes: Record<string, () => Response>): void {
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const path = url.replace('/api/v1', '');
        const handler = Object.entries(routes).find(([route]) => path.startsWith(route))?.[1];
        return Promise.resolve(handler ? handler() : json({}, 404));
      }),
    );
  }

  it('mostra o formulario de login quando nao ha sessao', async () => {
    stubRoutes({
      '/auth/me': () => json({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401),
      '/health': () => json(health),
    });

    render(<App />);

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toBeInTheDocument();
  });

  it('mostra a sessao e os membros quando ja esta autenticado', async () => {
    stubRoutes({
      '/auth/me': () => json(session),
      '/health': () => json(health),
      '/organizations/members': () =>
        json({
          content: [
            {
              memberId: 'm-1',
              userId: 'u-1',
              email: 'admin@jhcomp.local',
              displayName: 'Administrador',
              role: 'ADMIN',
              status: 'ACTIVE',
              createdAt: '2026-01-15T12:00:00Z',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
    });

    render(<App />);

    expect(await screen.findByText('Administrador')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sair' })).toBeInTheDocument();
    expect(screen.getByText('org-1')).toBeInTheDocument();
  });

  it('autentica e troca para o painel de sessao', async () => {
    let authenticated = false;
    stubRoutes({
      '/auth/me': () =>
        authenticated ? json(session) : json({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401),
      '/auth/login': () => {
        authenticated = true;
        return json(session);
      },
      '/health': () => json(health),
      '/organizations/members': () =>
        json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false }),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'admin@jhcomp.local');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-de-teste-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('button', { name: 'Sair' })).toBeInTheDocument();
  });

  it('mostra mensagem generica quando a credencial e invalida', async () => {
    stubRoutes({
      '/auth/me': () => json({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401),
      '/auth/login': () =>
        json({ type: 'INVALID_CREDENTIALS', title: 'Invalid credentials', status: 401 }, 401),
      '/health': () => json(health),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'alguem@example.test');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-errada-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    // A mensagem não diz se o e-mail existe: distinguir permitiria enumerar contas.
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('E-mail ou senha inválidos.');
    expect(alert.textContent).not.toContain('não encontrado');
  });

  it('associa erro de validacao ao campo correspondente', async () => {
    stubRoutes({
      '/auth/me': () => json({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401),
      '/auth/login': () =>
        json(
          {
            type: 'VALIDATION_ERROR',
            status: 400,
            errors: [{ field: 'email', message: 'must be a valid email' }],
          },
          400,
        ),
      '/health': () => json(health),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'nao-e-email');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-de-teste-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    const emailInput = await screen.findByLabelText('E-mail');
    await waitFor(() => {
      expect(emailInput).toHaveAttribute('aria-invalid', 'true');
    });
    expect(screen.getByText('must be a valid email')).toBeInTheDocument();
  });

  it('avisa sobre excesso de tentativas', async () => {
    stubRoutes({
      '/auth/me': () => json({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401),
      '/auth/login': () => json({ type: 'TOO_MANY_REQUESTS', status: 429 }, 429),
      '/health': () => json(health),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'alguem@example.test');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-errada-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas');
  });

  it('operador ve aviso em vez de erro ao nao poder listar membros', async () => {
    stubRoutes({
      '/auth/me': () => json({ ...session, role: 'OPERATOR' }),
      '/health': () => json(health),
      '/organizations/members': () => json({ type: 'FORBIDDEN', status: 403 }, 403),
    });

    render(<App />);

    expect(await screen.findByText(/não permite listar membros/)).toBeInTheDocument();
  });
});
