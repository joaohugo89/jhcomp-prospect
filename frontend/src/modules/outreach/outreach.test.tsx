import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, lead, page, session } from '../../test/fakeApi';
import { OutreachSection } from './OutreachSection';
import { SuppressionPage } from './SuppressionPage';

const MESSAGE_ID = '33333333-3333-4333-8333-333333333333';

function context(overrides: Partial<{ blocked: boolean }> = {}) {
  const blocked = overrides.blocked ?? false;
  return {
    senderOrganization: 'JH.COMP',
    senderName: 'Operadora Exemplo',
    leadStatus: 'CONTACT_APPROVED',
    companyName: 'Barbearia Ficticia',
    segment: 'BARBERSHOP',
    city: 'Cidade Exemplo',
    state: 'MG',
    confirmedFindings: ['o site não abre com conexão segura (HTTPS)'],
    channels: [
      { channel: 'WHATSAPP', recipient: '(35) 98888-0000', blocked },
      { channel: 'EMAIL', recipient: 'contato@barbearia.example.test', blocked },
      { channel: 'INSTAGRAM', blocked },
      { channel: 'PHONE_CALL', recipient: '(35) 98888-0000', blocked },
    ],
  };
}

function message(status: string, claims: readonly string[] = []) {
  return {
    id: MESSAGE_ID,
    leadId: LEAD_ID,
    channel: 'WHATSAPP',
    body: 'Olá, equipe da Barbearia Ficticia.',
    unverifiedClaims: claims,
    status,
    generatedBy: 'fake:template-v1',
    promptVersion: 'outreach-v1',
    createdAt: '2026-11-06T12:00:00Z',
    updatedAt: '2026-11-06T12:00:00Z',
    ...(status === 'APPROVED' ? { approvedAt: '2026-11-06T12:05:00Z' } : {}),
  };
}

function renderSection(onRecorded = vi.fn<() => void>()) {
  render(<OutreachSection lead={lead} onRecorded={onRecorded} />);
  return onRecorded;
}

/** Outreach no lead (doc 08 §9, doc 10 §6). */
describe('Mensagens do lead', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mostra remetente e contexto e gera rascunho no canal escolhido', async () => {
    const generated = message('NEEDS_REVIEW', ['Sugere melhoria sem achado confirmado.']);
    let messages: unknown[] = [];
    const calls = fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context()),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page(messages)),
      [`POST /leads/${LEAD_ID}/messages/generate`]: () => {
        messages = [generated];
        return json(generated, 201);
      },
    });
    renderSection();

    expect(await screen.findByText('Operadora Exemplo · JH.COMP')).toBeInTheDocument();
    expect(screen.getByText('o site não abre com conexão segura (HTTPS)')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: /Instagram.*sem contato cadastrado/ })).toBeDisabled();

    await userEvent.selectOptions(screen.getByLabelText('Canal e destinatário'), 'EMAIL');
    await userEvent.click(screen.getByRole('button', { name: 'Gerar rascunho' }));

    expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ channel: 'EMAIL' });
    expect(await screen.findByText('Precisa de revisão')).toBeInTheDocument();
  });

  it('aprovar afirmação não verificada exige confirmar a revisão', async () => {
    const calls = fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context()),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page([message('NEEDS_REVIEW', ['Afirmação a revisar.'])])),
      [`POST /messages/${MESSAGE_ID}/approve`]: () => json(message('APPROVED')),
    });
    renderSection();

    const approve = await screen.findByRole('button', { name: 'Aprovar' });
    expect(screen.getByText('Afirmação a revisar.')).toBeInTheDocument();
    expect(approve).toBeDisabled();

    await userEvent.click(screen.getByLabelText(/Revisei estas afirmações/));
    await userEvent.click(approve);

    await waitFor(() => {
      expect(calls.find((call) => call.path.endsWith('/approve'))?.body).toEqual({ unverifiedClaimsReviewed: true });
    });
  });

  it('mensagem aprovada não tem botão de envio: só registro do que o operador já fez', async () => {
    const onRecorded = vi.fn<() => void>();
    const calls = fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context()),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page([message('APPROVED')])),
      [`POST /messages/${MESSAGE_ID}/record-send`]: () => json(message('SENT')),
    });
    renderSection(onRecorded);

    const form = await screen.findByRole('form', { name: 'Registrar envio' });
    expect(screen.getByText('Aprovada, não enviada')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Enviar/ })).not.toBeInTheDocument();

    await userEvent.click(within(form).getByRole('button', { name: 'Registrar envio' }));

    await waitFor(() => {
      expect(onRecorded).toHaveBeenCalled();
    });
    const body = calls.find((call) => call.path.endsWith('/record-send'))?.body;
    expect(body).toMatchObject({ result: 'SENT', markLeadContacted: false });
  });

  it('marcar contatado é escolha explícita e some quando o envio falhou', async () => {
    const calls = fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context()),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page([message('APPROVED')])),
      [`POST /messages/${MESSAGE_ID}/record-send`]: () => json(message('SENT')),
    });
    renderSection();

    const mark = await screen.findByLabelText('Mover o lead para “Contatado”');
    expect(mark).not.toBeChecked();
    await userEvent.click(screen.getByLabelText('Não consegui enviar'));
    expect(screen.queryByLabelText('Mover o lead para “Contatado”')).not.toBeInTheDocument();

    await userEvent.click(screen.getByLabelText('Enviei a mensagem'));
    await userEvent.click(screen.getByLabelText('Mover o lead para “Contatado”'));
    await userEvent.click(screen.getByRole('button', { name: 'Registrar envio' }));

    await waitFor(() => {
      expect(calls.find((call) => call.path.endsWith('/record-send'))?.body).toMatchObject({ markLeadContacted: true });
    });
  });

  it('empresa bloqueada não gera nem aprova', async () => {
    fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context({ blocked: true })),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page([message('DRAFT')])),
    });
    renderSection();

    expect(await screen.findByText(/Há pedido de não contato/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gerar novo rascunho' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Aprovar' })).toBeDisabled();
  });

  it('descartar pede confirmação', async () => {
    const calls = fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context()),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page([message('DRAFT')])),
      [`POST /messages/${MESSAGE_ID}/cancel`]: () => json(message('CANCELLED')),
    });
    renderSection();

    await userEvent.click(await screen.findByRole('button', { name: 'Descartar' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Descartar mensagem?' });
    expect(calls.some((call) => call.path.endsWith('/cancel'))).toBe(false);

    await userEvent.click(within(dialog).getByRole('button', { name: 'Descartar' }));
    await waitFor(() => {
      expect(calls.some((call) => call.path.endsWith('/cancel'))).toBe(true);
    });
  });

  it('falha do provedor de IA avisa que nada foi salvo', async () => {
    fakeApi({
      [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(context()),
      [`GET /leads/${LEAD_ID}/messages`]: () => json(page([])),
      [`POST /leads/${LEAD_ID}/messages/generate`]: () => json({ type: 'AI_PROVIDER_ERROR', status: 502 }, 502),
    });
    renderSection();

    await userEvent.click(await screen.findByRole('button', { name: 'Gerar rascunho' }));

    expect(await screen.findByText(/Nada foi salvo/)).toBeInTheDocument();
  });
});

describe('Não contato', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('operador registra pedido, mas não consulta a lista', async () => {
    const calls = fakeApi({
      'POST /suppression-entries': () => json({ id: 's-1' }, 201),
    });
    render(<SuppressionPage session={{ ...session, role: 'OPERATOR' }} />);

    expect(screen.getByText(/Somente administradores consultam a lista de bloqueios/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/^Contato(?! a conferir)/), '(35) 98888-0000');
    await userEvent.type(screen.getByLabelText(/^Motivo/), 'Pediu por telefone');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar pedido' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Pedido registrado');
    expect(calls.some((call) => call.method === 'GET')).toBe(false);
    expect(calls[0]?.body).toEqual({
      identifierType: 'PHONE',
      identifier: '(35) 98888-0000',
      reason: 'Pediu por telefone',
    });
  });

  it('administrador vê a lista com o escopo de cada bloqueio', async () => {
    const calls = fakeApi({
      'GET /suppression-entries/summary': () => json({ PHONE: 3, EMAIL: 1, INSTAGRAM: 0 }),
      'GET /suppression-entries': () =>
        json(
          page([
            {
              id: 's-1',
              identifierType: 'EMAIL',
              identifier: 'contato@example.test',
              reason: 'Pedido por e-mail',
              requestedAt: '2026-11-06T12:00:00Z',
              createdAt: '2026-11-06T12:00:00Z',
            },
          ]),
        ),
    });
    render(<SuppressionPage session={session} />);

    const table = await screen.findByRole('table', { name: 'Pedidos de não contato' });
    expect(within(table).getByText('contato@example.test')).toBeInTheDocument();
    expect(within(table).getByText('Todos')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Buscar contato'), ' contato@example.test ');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => {
      const listed = calls.filter((call) => call.path === '/suppression-entries');
      expect(listed.at(-1)?.query.get('search')).toBe('contato@example.test');
    });
  });

  it('qualquer membro confere um contato e ve so se esta bloqueado', async () => {
    const calls = fakeApi({
      'POST /suppression-entries/check': () =>
        json({ recognized: true, blocked: true, allChannels: false, channels: ['WHATSAPP'] }),
    });
    render(<SuppressionPage session={{ ...session, role: 'OPERATOR' }} />);

    await userEvent.type(screen.getByLabelText('Contato a conferir'), '(35) 98888-0000');
    await userEvent.click(screen.getByRole('button', { name: 'Conferir' }));

    expect(await screen.findByText('Bloqueado em: WhatsApp.')).toBeInTheDocument();
    expect(calls[0]?.body).toEqual({ identifierType: 'PHONE', identifier: '(35) 98888-0000' });
  });

  it('motivo frequente preenche o campo e o tipo muda o exemplo', async () => {
    fakeApi({});
    render(<SuppressionPage session={{ ...session, role: 'OPERATOR' }} />);

    await userEvent.click(screen.getByRole('button', { name: 'Respondeu pedindo para parar' }));
    expect(screen.getByLabelText(/^Motivo/)).toHaveValue('Respondeu pedindo para parar');
    const group = screen.getByRole('group', { name: 'Tipo do contato a conferir' });
    await userEvent.click(within(group).getByLabelText('E-mail'));
    expect(screen.getByLabelText('Contato a conferir')).toHaveAttribute('placeholder', 'contato@empresa.com.br');
  });

  it('contato que o servidor não reconhece mostra mensagem específica', async () => {
    fakeApi({
      'POST /suppression-entries': () => json({ type: 'VALIDATION_ERROR', status: 400 }, 400),
    });
    render(<SuppressionPage session={{ ...session, role: 'OPERATOR' }} />);

    await userEvent.type(screen.getByLabelText(/^Contato(?! a conferir)/), '98888');
    await userEvent.type(screen.getByLabelText(/^Motivo/), 'Pedido');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar pedido' }));

    expect(await screen.findByText('Não foi possível reconhecer o telefone informado.')).toBeInTheDocument();
  });
});
