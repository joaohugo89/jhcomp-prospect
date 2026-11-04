import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from '../../App';
import { fakeApi, json, page, session, leadListRoutes, worklistItem } from '../../test/fakeApi';
import { ChangePasswordForm } from '../auth/ChangePasswordForm';
import { MembersPage } from './MembersPage';

const NEW_PASSWORD = 'frase-longa-ficticia';

const self = {
  memberId: 'm-1',
  userId: session.userId,
  email: session.email,
  displayName: session.displayName,
  role: 'ADMIN',
  status: 'ACTIVE',
  passwordChangeRequired: false,
  lastLoginAt: '2026-11-06T09:00:00Z',
  createdAt: '2026-11-01T12:00:00Z',
};

const operator = {
  memberId: 'm-2',
  userId: 'u-2',
  email: 'operadora@example.test',
  displayName: 'Operadora Ficticia',
  role: 'OPERATOR',
  status: 'ACTIVE',
  passwordChangeRequired: false,
  createdAt: '2026-11-02T12:00:00Z',
};

async function fillPasswordForm(current: string, next: string, confirmation = next): Promise<void> {
  await userEvent.type(screen.getByLabelText(/^Senha atual/), current);
  await userEvent.type(screen.getByLabelText(/^Nova senha/), next);
  await userEvent.type(screen.getByLabelText(/^Repita a nova senha/), confirmation);
}

/** Senha temporária e troca de senha (D28). */
describe('Troca de senha', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/leads');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('com senha temporária, só mostra a troca; depois dela, a aplicação', async () => {
    const calls = fakeApi({
      'GET /auth/me': () => json({ ...session, passwordChangeRequired: true }),
      'POST /auth/password': () => json({ ...session, passwordChangeRequired: false }),
      ...leadListRoutes([worklistItem]),
    });
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Defina sua senha' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Leads' })).not.toBeInTheDocument();
    expect(calls.some((call) => call.path === '/leads')).toBe(false);

    await fillPasswordForm('temporaria-ficticia', NEW_PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Trocar senha' }));

    expect(await screen.findByRole('button', { name: 'Barbearia Ficticia' })).toBeInTheDocument();
    expect(calls.find((call) => call.path === '/auth/password')?.body).toEqual({
      currentPassword: 'temporaria-ficticia',
      newPassword: NEW_PASSWORD,
    });
  });

  it('exige 12 caracteres e confirmação igual antes de enviar', async () => {
    fakeApi({});
    render(<ChangePasswordForm onChanged={vi.fn<() => void>()} />);

    await fillPasswordForm('atual-ficticia', 'curta');
    expect(screen.getByText('Use ao menos 12 caracteres.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Trocar senha' })).toBeDisabled();

    await userEvent.clear(screen.getByLabelText(/^Nova senha/));
    await userEvent.type(screen.getByLabelText(/^Nova senha/), NEW_PASSWORD);
    expect(screen.getByText('As senhas não conferem.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Trocar senha' })).toBeDisabled();
  });

  it('senha atual errada mostra aviso sem encerrar a sessão', async () => {
    fakeApi({ 'POST /auth/password': () => json({ type: 'INVALID_CREDENTIALS', status: 400 }, 400) });
    const onChanged = vi.fn<() => void>();
    render(<ChangePasswordForm onChanged={onChanged} />);

    await fillPasswordForm('errada-ficticia', NEW_PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Trocar senha' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A senha atual não confere.');
    expect(onChanged).not.toHaveBeenCalled();
  });
});

/** Gestão de membros (RF-003, D28). */
describe('Membros', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('a própria linha não tem ações; a dos outros, sim', async () => {
    fakeApi({ 'GET /organizations/members': () => json(page([self, operator])) });
    render(<MembersPage session={session} />);

    const table = await screen.findByRole('table', { name: 'Membros' });
    const rows = within(table).getAllByRole('row');
    const selfRow = rows.find((row) => within(row).queryByText(session.email) !== null);
    const operatorRow = rows.find((row) => within(row).queryByText('Operadora Ficticia') !== null);
    expect(selfRow).toBeDefined();
    expect(operatorRow).toBeDefined();
    if (selfRow === undefined || operatorRow === undefined) {
      return;
    }
    expect(within(selfRow).getByText('Você')).toBeInTheDocument();
    expect(within(selfRow).queryByRole('button')).not.toBeInTheDocument();
    expect(within(operatorRow).getByRole('button', { name: 'Remover acesso' })).toBeInTheDocument();
  });

  it('cadastra membro e mostra a senha temporária uma vez', async () => {
    let members: readonly unknown[] = [self];
    const calls = fakeApi({
      'GET /organizations/members': () => json(page(members)),
      'POST /organizations/members': () => {
        members = [self, operator];
        return json({ member: operator, temporaryPassword: 'Tmp7kQ2mXa9pLr4sWv8z' }, 201);
      },
    });
    render(<MembersPage session={session} />);

    await userEvent.type(await screen.findByLabelText(/^Nome/), 'Operadora Ficticia');
    await userEvent.type(screen.getByLabelText(/^E-mail/), 'operadora@example.test');
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar membro' }));

    const notice = await screen.findByRole('status');
    expect(within(notice).getByText('Tmp7kQ2mXa9pLr4sWv8z')).toBeInTheDocument();
    expect(notice).toHaveTextContent('não será mostrada de novo');
    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({
      email: 'operadora@example.test',
      displayName: 'Operadora Ficticia',
      role: 'OPERATOR',
    });

    await userEvent.click(within(notice).getByRole('button', { name: 'Já entreguei a senha' }));
    expect(screen.queryByText('Tmp7kQ2mXa9pLr4sWv8z')).not.toBeInTheDocument();
  });

  it('remover acesso e redefinir senha pedem confirmação', async () => {
    const calls = fakeApi({
      'GET /organizations/members': () => json(page([self, operator])),
      'PATCH /organizations/members/m-2': () => json({ ...operator, status: 'REVOKED' }),
      'POST /organizations/members/m-2/reset-password': () =>
        json({ member: operator, temporaryPassword: 'Nova2senhaTemporaria' }),
    });
    render(<MembersPage session={session} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Remover acesso' }));
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false);
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Remover acesso' }));
    await waitFor(() => {
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ status: 'REVOKED' });
    });

    await userEvent.click(await screen.findByRole('button', { name: 'Redefinir senha' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Redefinir senha' }));
    expect(await screen.findByText('Nova2senhaTemporaria')).toBeInTheDocument();
  });

  it('mostra a visão da equipe, quem não fez o primeiro acesso e filtra no servidor', async () => {
    const calls = fakeApi({
      'GET /organizations/members/summary': () => json({ active: 4, admins: 1, revoked: 2, awaitingFirstAccess: 1 }),
      'GET /organizations/members': () =>
        json(
          page([
            self,
            { ...operator, passwordChangeRequired: true },
            { ...operator, memberId: 'm-3', userId: 'u-3', status: 'REVOKED' },
          ]),
        ),
    });
    render(<MembersPage session={session} />);

    const table = await screen.findByRole('table', { name: 'Membros' });
    expect(within(table).getByText('Senha temporária')).toBeInTheDocument();
    expect(within(table).getByText('Acesso removido')).toBeInTheDocument();
    expect(within(table).getAllByText('Nunca entrou').length).toBeGreaterThan(0);
    expect(within(table).getByRole('button', { name: 'Reativar acesso' })).toBeInTheDocument();
    expect(await screen.findByText('Aguardando 1º acesso')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Buscar por nome ou e-mail'), ' operadora ');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await userEvent.click(screen.getByRole('button', { name: 'Removidos' }));
    await waitFor(() => {
      const query = calls.filter((call) => call.path === '/organizations/members').at(-1)?.query;
      expect(query?.get('search')).toBe('operadora');
      expect(query?.get('status')).toBe('REVOKED');
    });
  });

  it('mudar papel pede confirmação e envia só o papel', async () => {
    const calls = fakeApi({
      'GET /organizations/members': () => json(page([self, operator])),
      'PATCH /organizations/members/m-2': () => json({ ...operator, role: 'ADMIN' }),
    });
    render(<MembersPage session={session} />);

    await userEvent.selectOptions(await screen.findByLabelText('Papel de Operadora Ficticia'), 'ADMIN');
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Mudar papel' }));

    await waitFor(() => {
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({ role: 'ADMIN' });
    });
  });
});
