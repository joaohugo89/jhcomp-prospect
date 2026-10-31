import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, lead, page } from '../../test/fakeApi';
import { DashboardPage } from '../dashboard/DashboardPage';
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
  allowedTransitions: ['QUALIFIED', 'CONTACTED', 'LOST', 'DO_NOT_CONTACT'],
};

function board(total = 1) {
  return STAGES.map(([key, name], position) => ({
    stage: { key, name, position, terminal: key === 'WON' || key === 'LOST' },
    total: key === 'CONTACT_APPROVED' ? total : 0,
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

/** Kanban: mover card é mudar status do lead (D2). */
describe('Pipeline', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mostra as etapas, o total e o card com o que o operador precisa', async () => {
    fakeApi({ 'GET /pipeline/board': () => json(board(60)) });

    render(<PipelinePage />);

    const approved = await screen.findByRole('region', { name: /^Contato aprovado/ });
    expect(within(approved).getByRole('link', { name: 'Barbearia Ficticia' })).toHaveAttribute(
      'href',
      `/leads/${LEAD_ID}`,
    );
    expect(within(approved).getByText('Alta')).toBeInTheDocument();
    expect(within(approved).getByText(/Próxima tarefa: Ligar de novo/)).toBeInTheDocument();
    expect(within(approved).getByText(/Mostrando 1 de 60/)).toBeInTheDocument();
    expect(screen.getAllByRole('region')).toHaveLength(STAGES.length);
    expect(screen.queryByRole('region', { name: /Não contatar/ })).not.toBeInTheDocument();
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
    ...overrides,
  });
}

/** Negócio, interações e tarefas no detalhe do lead (D25). */
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
    // Sem resultado no negócio: ganho ou perdido é o status do lead (D2).
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
    // O histórico é relido para mostrar a interação.
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

    // Atraso indicado por texto, não só por cor.
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

/** Indicadores com o período explícito (RF-071). */
describe('Dashboard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const summary = {
    from: '2026-10-07',
    to: '2026-11-05',
    summary: {
      leadsByStatus: { NEW: 3, WON: 1 },
      activeLeads: 3,
      companiesAdded: 5,
      contactsRegistered: 2,
      openProposals: 1,
      dealsWon: 1,
      wonValue: 2500,
      openTasks: 4,
      overdueTasks: 1,
    },
  };

  it('mostra indicadores, periodo e atividade', async () => {
    fakeApi({
      'GET /dashboard/summary': () => json(summary),
      'GET /dashboard/activity': () =>
        json(
          page([
            {
              type: 'STATUS_CHANGE',
              leadId: LEAD_ID,
              companyName: 'Barbearia Ficticia',
              occurredAt: '2026-11-04T12:00:00Z',
              fromStatus: 'PROPOSAL_SENT',
              toStatus: 'WON',
            },
          ]),
        ),
    });

    render(<DashboardPage />);

    const period = await screen.findByRole('region', { name: 'No período (07/10/2026 a 05/11/2026)' });
    expect(within(period).getByText('Empresas adicionadas').nextElementSibling).toHaveTextContent('5');
    expect(within(period).getByText(/2\.500,00/)).toBeInTheDocument();
    expect(screen.getByText('Tarefas atrasadas').nextElementSibling).toHaveTextContent('1');
    expect(await screen.findByText('Proposta enviada → Ganho')).toBeInTheDocument();
  });

  it('envia o periodo escolhido e recusa inicio depois do fim', async () => {
    const calls = fakeApi({
      'GET /dashboard/summary': () => json(summary),
      'GET /dashboard/activity': () => json(page([])),
    });

    render(<DashboardPage />);
    await screen.findByText('Nenhuma atividade no período.');

    const from = screen.getByLabelText('De');
    const to = screen.getByLabelText('Até');
    fireEvent.change(from, { target: { value: '2026-11-10' } });
    fireEvent.change(to, { target: { value: '2026-11-01' } });
    expect(screen.getByRole('button', { name: 'Aplicar' })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('A data inicial deve ser anterior à final.');

    fireEvent.change(from, { target: { value: '2026-10-01' } });
    await userEvent.click(screen.getByRole('button', { name: 'Aplicar' }));

    await waitFor(() => {
      const last = calls.filter((call) => call.path === '/dashboard/summary').at(-1);
      expect(last?.query.get('from')).toBe('2026-10-01');
      expect(last?.query.get('to')).toBe('2026-11-01');
    });
  });
});
