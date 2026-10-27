import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App';
import { fakeApi, json, lead, page, session } from './test/fakeApi';

const unauthenticated = (): Response => json({ type: 'AUTHENTICATION_REQUIRED', status: 401 }, 401);

/**
 * Fluxo de autenticação, estrutura da aplicação e estados globais (doc 08 §11).
 */
describe('App', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mostra o login quando nao ha sessao', async () => {
    fakeApi({ 'GET /auth/me': unauthenticated });

    render(<App />);

    expect(await screen.findByRole('button', { name: 'Entrar' })).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail')).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toBeInTheDocument();
  });

  it('autenticado, abre a lista de leads dentro da estrutura da aplicacao', async () => {
    fakeApi({
      'GET /auth/me': () => json(session),
      'GET /leads': () => json(page([lead])),
    });

    render(<App />);

    expect(await screen.findByRole('link', { name: 'Barbearia Ficticia' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Leads' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Principal' });
    expect(nav).toHaveTextContent('Empresas');
    expect(nav).toHaveTextContent('Membros');
    expect(screen.getByRole('link', { name: 'Leads' })).toHaveAttribute('aria-current', 'page');
    // Status com texto, não só cor (doc 08 §12).
    expect(within(screen.getByRole('table')).getByText('Contato aprovado')).toBeInTheDocument();
  });

  it('operador nao ve o item de membros', async () => {
    fakeApi({
      'GET /auth/me': () => json({ ...session, role: 'OPERATOR' }),
      'GET /leads': () => json(page([])),
    });

    render(<App />);

    await screen.findByRole('heading', { level: 1, name: 'Leads' });
    expect(screen.getByRole('navigation', { name: 'Principal' })).not.toHaveTextContent('Membros');
  });

  it('autentica e entra na aplicacao', async () => {
    let authenticated = false;
    fakeApi({
      'GET /auth/me': () => (authenticated ? json(session) : unauthenticated()),
      'POST /auth/login': () => {
        authenticated = true;
        return json(session);
      },
      'GET /leads': () => json(page([])),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'admin@jhcomp.local');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-de-teste-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('button', { name: 'Sair' })).toBeInTheDocument();
    expect(await screen.findByText('Nenhum lead ainda.')).toBeInTheDocument();
  });

  it('mostra mensagem generica quando a credencial e invalida', async () => {
    fakeApi({
      'GET /auth/me': unauthenticated,
      'POST /auth/login': () => json({ type: 'INVALID_CREDENTIALS', title: 'Invalid credentials', status: 401 }, 401),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'alguem@example.test');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-errada-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    // A mensagem não diz se o e-mail existe: distinguir permitiria enumerar contas.
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('E-mail ou senha inválidos.');
    expect(alert.textContent).not.toContain('não encontrado');
    // Falha de login não é "sessão expirada".
    expect(screen.queryByText(/sessão expirou/)).not.toBeInTheDocument();
  });

  it('associa erro de validacao ao campo correspondente', async () => {
    fakeApi({
      'GET /auth/me': unauthenticated,
      'POST /auth/login': () =>
        json({ type: 'VALIDATION_ERROR', status: 400, errors: [{ field: 'email', message: 'must be a valid email' }] }, 400),
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
    fakeApi({
      'GET /auth/me': unauthenticated,
      'POST /auth/login': () => json({ type: 'TOO_MANY_REQUESTS', status: 429 }, 429),
    });

    render(<App />);

    await userEvent.type(await screen.findByLabelText('E-mail'), 'alguem@example.test');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-errada-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Muitas tentativas');
  });

  it('volta ao login explicando quando a sessao expira durante o uso', async () => {
    fakeApi({
      'GET /auth/me': () => json(session),
      'GET /leads': unauthenticated,
    });

    render(<App />);

    expect(await screen.findByText(/Sua sessão expirou/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument();
  });

  it('navega pelo menu sem recarregar a pagina', async () => {
    fakeApi({
      'GET /auth/me': () => json(session),
      'GET /leads': () => json(page([])),
      'GET /companies': () => json(page([])),
    });

    render(<App />);

    await userEvent.click(await screen.findByRole('link', { name: 'Empresas' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Empresas' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/companies');
    expect(await screen.findByText('Nenhuma empresa cadastrada ainda.')).toBeInTheDocument();
  });

  it('rota desconhecida mostra pagina nao encontrada', async () => {
    window.history.replaceState(null, '', '/nao-existe');
    fakeApi({ 'GET /auth/me': () => json(session) });

    render(<App />);

    expect(await screen.findByText('Página não encontrada.')).toBeInTheDocument();
  });

  it('lead de outra organizacao aparece como nao encontrado', async () => {
    window.history.replaceState(null, '', `/leads/${lead.id}`);
    fakeApi({
      'GET /auth/me': () => json(session),
      'GET /leads/': () => json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404),
    });

    render(<App />);

    expect(await screen.findByText('Lead não encontrado.')).toBeInTheDocument();
  });

  it('operador que abre membros pelo endereco ve aviso de permissao', async () => {
    window.history.replaceState(null, '', '/settings/members');
    fakeApi({
      'GET /auth/me': () => json({ ...session, role: 'OPERATOR' }),
      'GET /organizations/members': () => json({ type: 'FORBIDDEN', status: 403 }, 403),
    });

    render(<App />);

    expect(await screen.findByText('Sem permissão')).toBeInTheDocument();
  });
});
