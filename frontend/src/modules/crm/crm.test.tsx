import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, lead, outreachContext, page } from '../../test/fakeApi';
import { LeadDetailPage } from '../leads/LeadDetailPage';
import { PipelinePage } from './PipelinePage';

const NOT_FOUND = (): Response => json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404);

const STAGES = [
  ['NEW', 'Novo'],
  ['QUALIFIED', 'Qualificado'],
  ['CONTACT_APPROVED', 'Contato aprovado'],
  ['CONTACTED', 'Contatado'],
  ['IN_CONVERSATION', 'Em conversa'],
  ['PROPOSAL_SENT', 'Proposta enviada'],
  ['WON', 'Ganho'],
  ['LOST', 'Perdido'],
] as const;

const card = {
  leadId: LEAD_ID,
  companyName: 'Barbearia Ficticia',
  segment: 'BARBERSHOP',
  city: 'Cidade Exemplo',
  state: 'MG',
  status: 'CONTACT_APPROVED',
  priority: 'HIGH',
  score: 70,
  nextTaskTitle: 'Ligar de novo',
  estimatedValue: 1500,
  stageEnteredAt: new Date(Date.now() - 10 * 86_400_000).toISOString(),
  allowedTransitions: ['QUALIFIED', 'CONTACTED', 'LOST', 'DO_NOT_CONTACT'],
};

function board(total = 1) {
  return STAGES.map(([key, name], position) => ({
    stage: { key, name, position, terminal: key === 'WON' || key === 'LOST' },
    total: key === 'CONTACT_APPROVED' ? total : 0,
    totalValue: key === 'CONTACT_APPROVED' ? 4200 : 0,
    ...(key === 'WON' || key === 'LOST' ? { recentDays: 30 } : {}),
    cards: key === 'CONTACT_APPROVED' ? [card] : [],
  }));
}

function column(name: string): HTMLElement {
  return screen.getByRole('region', { name: new RegExp(`^${name}`) });
}

function drag(target: string): void {
  const dataTransfer = { setData: vi.fn<(format: string, data: string) => void>(), effectAllowed: '' };
  fireEvent.dragStart(screen.getByRole('article'), { dataTransfer });
  fireEvent.dragOver(column(target), { dataTransfer });
  fireEvent.drop(column(target), { dataTransfer });
}

/** Kanban: moving a card is changing the lead's status (D2). */
describe('Pipeline', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.localStorage.clear();
  });

  it('mostra as etapas, o total, o valor e o card compacto com sinais de acao', async () => {
    fakeApi({ 'GET /pipeline/board': () => json(board(60)) });

    render(<PipelinePage />);

    const approved = await screen.findByRole('region', { name: /^Contato aprovado/ });
    expect(within(approved).getByRole('link', { name: 'Barbearia Ficticia' })).toHaveAttribute(
      'href',
      `/leads/${LEAD_ID}`,
    );
    // Header: the count and value of the whole stage, not only of the cards shown.
    expect(within(approved).getByLabelText('60 leads')).toBeInTheDocument();
    expect(within(approved).getByText(/4\.200,00/)).toBeInTheDocument();
    expect(within(approved).getByText(/1\.500,00/)).toBeInTheDocument();
    expect(within(approved).getByText('10 dias na etapa')).toBeInTheDocument();
    expect(within(approved).getByText(/Mostrando 1 de 60/)).toBeInTheDocument();
    // Compact: priority and next task are left to the detailed density, and "no analysis" is not shown.
    expect(within(approved).queryByText('Alta')).not.toBeInTheDocument();
    expect(within(approved).queryByText(/Sem análise/)).not.toBeInTheDocument();
    expect(screen.getAllByRole('region')).toHaveLength(STAGES.length);
    expect(screen.queryByRole('region', { name: /Não contatar/ })).not.toBeInTheDocument();

    const summary = screen.getByLabelText('Resumo do quadro');
    expect(summary).toHaveTextContent(/Em aberto60 leads · R\$\s4\.200,00/);
    expect(summary).toHaveTextContent('Ganho nos últimos 30 dias');
  });

  it('modo detalhado mostra prioridade, score e proxima tarefa e fica guardado', async () => {
    fakeApi({ 'GET /pipeline/board': () => json(board()) });

    const { unmount } = render(<PipelinePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Detalhado' }));

    const approved = screen.getByRole('region', { name: /^Contato aprovado/ });
    expect(within(approved).getByText('Alta')).toBeInTheDocument();
    expect(within(approved).getByLabelText('Score 70 de 100')).toBeInTheDocument();
    expect(within(approved).getByText(/Próxima tarefa: Ligar de novo/)).toBeInTheDocument();

    unmount();
    render(<PipelinePage />);
    expect(await screen.findByRole('button', { name: 'Detalhado' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('sinaliza tarefa atrasada, lead parado e sem interacao, com texto e nao so cor', async () => {
    const stale = {
      ...card,
      nextTaskDueAt: new Date(Date.now() - 86_400_000).toISOString(),
      lastInteractionType: 'CONTACT',
      lastInteractionAt: new Date(Date.now() - 20 * 86_400_000).toISOString(),
    };
    fakeApi({
      'GET /pipeline/board': () =>
        json(board().map((item) => (item.cards.length > 0 ? { ...item, cards: [stale] } : item))),
    });

    render(<PipelinePage />);

    const sinais = await screen.findByRole('list', { name: 'Situação do lead' });
    expect(sinais).toHaveTextContent('Tarefa atrasada');
    expect(sinais).toHaveTextContent('10 dias na etapa');
    expect(sinais).toHaveTextContent('Sem interação há 20 dias');
  });

  it('coluna vazia comeca recolhida, aceita expandir e lembra a escolha', async () => {
    fakeApi({ 'GET /pipeline/board': () => json(board()) });

    const { unmount } = render(<PipelinePage />);
    const expandir = await screen.findByRole('button', { name: 'Expandir Contatado' });
    expect(screen.getByRole('region', { name: /^Contatado/ })).toHaveTextContent('Contatado · 0');
    expect(screen.queryByRole('button', { name: 'Expandir Contato aprovado' })).not.toBeInTheDocument();

    await userEvent.click(expandir);
    expect(screen.getByRole('region', { name: /^Contatado/ })).toHaveTextContent('Nenhum lead nesta etapa.');

    await userEvent.click(screen.getByRole('button', { name: 'Recolher Contato aprovado' }));
    expect(screen.queryByRole('article')).not.toBeInTheDocument();

    unmount();
    render(<PipelinePage />);
    expect(await screen.findByRole('button', { name: 'Recolher Contatado' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Expandir Contato aprovado' })).toBeInTheDocument();
  });

  it('colunas finais avisam a janela de 30 dias e apontam a lista completa', async () => {
    fakeApi({ 'GET /pipeline/board': () => json(board()) });

    render(<PipelinePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Expandir Ganho' }));

    const ganho = screen.getByRole('region', { name: /^Ganho/ });
    expect(ganho).toHaveTextContent('últimos 30 dias');
    expect(within(ganho).getByRole('link', { name: 'lista de leads' })).toHaveAttribute('href', '/leads');
  });

  it('move pelo botao, sem arrastar', async () => {
    const calls = fakeApi({
      'GET /pipeline/board': () => json(board()),
      'PATCH /leads/': () => json({ ...lead, status: 'QUALIFIED' }),
    });

    render(<PipelinePage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Mover Barbearia Ficticia' }));
    await userEvent.selectOptions(screen.getByLabelText('Novo status'), 'Qualificado');
    await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }));

    await waitFor(() => {
      expect(calls.filter((call) => call.method === 'GET')).toHaveLength(2);
    });
    const patch = calls.find((call) => call.method === 'PATCH');
    expect(patch?.path).toBe(`/leads/${LEAD_ID}/status`);
    expect(patch?.body).toEqual({ status: 'QUALIFIED', contactConfirmed: false });
  });

  it('soltar numa etapa simples aplica direto', async () => {
    const calls = fakeApi({
      'GET /pipeline/board': () => json(board()),
      'PATCH /leads/': () => json({ ...lead, status: 'QUALIFIED' }),
    });

    render(<PipelinePage />);
    await screen.findByRole('article');
    drag('Qualificado');

    await waitFor(() => {
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
        status: 'QUALIFIED',
        contactConfirmed: false,
      });
    });
  });

  it('soltar em CONTACTED nao envia: pede a confirmacao de contato', async () => {
    const calls = fakeApi({ 'GET /pipeline/board': () => json(board()) });

    render(<PipelinePage />);
    await screen.findByRole('article');
    drag('Contatado');

    expect(await screen.findByLabelText('Novo status')).toHaveValue('CONTACTED');
    expect(screen.getByRole('button', { name: 'Alterar status' })).toBeDisabled();
    expect(screen.getByLabelText(/contato com a empresa foi de fato realizado/)).not.toBeChecked();
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false);
  });

  it('soltar em estado final pede confirmacao antes de enviar', async () => {
    const calls = fakeApi({ 'GET /pipeline/board': () => json(board()) });

    render(<PipelinePage />);
    await screen.findByRole('article');
    drag('Perdido');

    expect(await screen.findByLabelText('Novo status')).toHaveValue('LOST');
    expect(screen.getByLabelText('Motivo da perda (opcional)')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }));
    expect(await screen.findByRole('alertdialog')).toHaveTextContent('Mudar para "Perdido"?');
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false);
  });

  it('etapa fora das transicoes permitidas nao aceita o card', async () => {
    const calls = fakeApi({ 'GET /pipeline/board': () => json(board()) });

    render(<PipelinePage />);
    await screen.findByRole('article');
    drag('Ganho');

    expect(screen.queryByLabelText('Novo status')).not.toBeInTheDocument();
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false);
  });

  it('quadro vazio orienta a abrir um lead', async () => {
    fakeApi({ 'GET /pipeline/board': () => json(board(0).map((item) => ({ ...item, cards: [] }))) });

    render(<PipelinePage />);

    expect(await screen.findByText('Nenhum lead na pipeline.')).toBeInTheDocument();
  });
});

const deal = {
  leadId: LEAD_ID,
  service: 'Site institucional',
  currency: 'BRL',
  outcome: 'OPEN',
  updatedAt: '2026-11-03T12:00:00Z',
};

function leadRoutes(overrides: Record<string, () => Response> = {}) {
  const base = `/leads/${LEAD_ID}`;
  return fakeApi({
    'GET /leads/': () => json(lead),
    [`GET ${base}/analyses`]: () => json(page([])),
    [`GET ${base}/timeline`]: () => json(page([])),
    [`GET ${base}/tasks`]: () => json(page([])),
    [`GET ${base}/deal`]: NOT_FOUND,
    [`GET ${base}/outreach-context`]: () => json(outreachContext),
    [`GET ${base}/messages`]: () => json(page([])),
    ...overrides,
  });
}

/** Deal, interactions and tasks in the lead detail (D25). */
describe('CRM no detalhe do lead', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('antes da proposta nao ha negocio, e isso nao e erro', async () => {
    leadRoutes();

    render(<LeadDetailPage id={LEAD_ID} />);

    expect(await screen.findByText('O negócio é criado quando a proposta é enviada.')).toBeInTheDocument();
  });

  it('salva servico e valor do negocio', async () => {
    const calls = leadRoutes({
      [`GET /leads/${LEAD_ID}/deal`]: () => json(deal),
      'PUT /leads/': () => json({ ...deal, estimatedValue: 1500.5 }),
    });

    render(<LeadDetailPage id={LEAD_ID} />);
    await userEvent.type(await screen.findByLabelText('Valor estimado (R$)'), '1500.50');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar negócio' }));

    expect(await screen.findByText(/1\.500,50/)).toBeInTheDocument();
    expect(calls.find((call) => call.method === 'PUT')?.body).toEqual({
      service: 'Site institucional',
      estimatedValue: 1500.5,
      lossReason: '',
    });
    // No outcome on the deal: won or lost is the lead's status (D2).
    expect(screen.queryByLabelText('Motivo da perda')).not.toBeInTheDocument();
  });

  it('registrar contato nao muda o status do lead', async () => {
    const calls = leadRoutes({
      'POST /leads/': () =>
        json(
          {
            id: 'i-1',
            type: 'CONTACT',
            channel: 'WHATSAPP',
            direction: 'OUTBOUND',
            occurredAt: '2026-11-04T12:00:00Z',
          },
          201,
        ),
    });

    render(<LeadDetailPage id={LEAD_ID} />);
    await userEvent.type(await screen.findByLabelText('Resultado'), 'Pediu retorno');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar interação' }));

    expect(await screen.findByText('Interação registrada.')).toBeInTheDocument();
    const post = calls.find((call) => call.method === 'POST');
    expect(post?.path).toBe(`/leads/${LEAD_ID}/interactions`);
    expect(post?.body).toMatchObject({
      type: 'CONTACT',
      channel: 'WHATSAPP',
      direction: 'OUTBOUND',
      outcome: 'Pediu retorno',
    });
    expect(post?.body).toHaveProperty('occurredAt', expect.any(String));
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false);
    // The history is re-read so the interaction shows up.
    expect(calls.filter((call) => call.path.endsWith('/timeline'))).toHaveLength(2);
  });

  it('pedido de nao contato pede confirmacao e rele o lead', async () => {
    const calls = leadRoutes({ 'POST /leads/': () => json({ id: 'i-1' }, 201) });

    render(<LeadDetailPage id={LEAD_ID} />);
    await userEvent.selectOptions(await screen.findByLabelText('Tipo'), 'Pedido de não contato');
    expect(screen.getByLabelText('Quem iniciou')).toHaveValue('INBOUND');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar interação' }));

    const dialog = await screen.findByRole('alertdialog');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(calls.some((call) => call.method === 'POST')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Registrar interação' }));
    await userEvent.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Registrar pedido' }),
    );

    await waitFor(() => {
      expect(calls.filter((call) => call.method === 'GET' && call.path === `/leads/${LEAD_ID}`)).toHaveLength(2);
    });
  });

  it('lead bloqueado nao aceita abordagem nossa, mas aceita o que partiu da empresa', async () => {
    leadRoutes({
      [`GET /leads/${LEAD_ID}`]: () => json({ ...lead, status: 'DO_NOT_CONTACT', allowedTransitions: [] }),
    });

    render(<LeadDetailPage id={LEAD_ID} />);

    const submit = await screen.findByRole('button', { name: 'Registrar interação' });
    expect(submit).toBeDisabled();
    expect(screen.getByText(/marcada como “Não contatar”/)).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'Resposta');
    expect(submit).toBeEnabled();
  });

  it('409 do servidor vira aviso de bloqueio', async () => {
    leadRoutes({ 'POST /leads/': () => json({ type: 'CONFLICT', status: 409 }, 409) });

    render(<LeadDetailPage id={LEAD_ID} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar interação' }));

    expect(await screen.findByText(/abordagem não pode ser registrada/)).toBeInTheDocument();
  });

  it('historico mostra a interacao', async () => {
    leadRoutes({
      [`GET /leads/${LEAD_ID}/timeline`]: () =>
        json(
          page([
            {
              type: 'INTERACTION',
              id: 'i-1',
              occurredAt: '2026-11-04T12:00:00Z',
              actorName: 'Operadora',
              interactionType: 'MEETING',
              channel: 'IN_PERSON',
              direction: 'OUTBOUND',
              outcome: 'Gostou da proposta',
            },
          ]),
        ),
    });

    render(<LeadDetailPage id={LEAD_ID} />);

    expect(await screen.findByText('Reunião · Presencial · Nós entramos em contato')).toBeInTheDocument();
    expect(screen.getByText('Gostou da proposta')).toBeInTheDocument();
  });

  it('cria tarefa e marca como concluida enviando o estado completo', async () => {
    const task = {
      id: 't-1',
      leadId: LEAD_ID,
      title: 'Enviar orçamento',
      dueAt: '2026-01-01T12:00:00Z',
      status: 'OPEN',
      createdAt: '2025-12-30T12:00:00Z',
    };
    const calls = leadRoutes({
      [`GET /leads/${LEAD_ID}/tasks`]: () => json(page([task])),
      'POST /tasks': () => json(task, 201),
      'PATCH /tasks/': () => json({ ...task, status: 'DONE' }),
    });

    render(<LeadDetailPage id={LEAD_ID} />);

    // The delay is shown as text, not colour alone.
    expect(await screen.findByText(/Atrasada/)).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Nova tarefa/), 'Ligar amanhã');
    await userEvent.click(screen.getByRole('button', { name: 'Adicionar' }));
    await waitFor(() => {
      expect(calls.find((call) => call.method === 'POST')?.body).toEqual({ leadId: LEAD_ID, title: 'Ligar amanhã' });
    });

    await userEvent.click(screen.getByLabelText('Concluída: Enviar orçamento'));
    await waitFor(() => {
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
        title: 'Enviar orçamento',
        dueAt: '2026-01-01T12:00:00Z',
        status: 'DONE',
      });
    });
  });
});
