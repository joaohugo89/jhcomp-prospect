import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  COMPANY_ID,
  LEAD_ID,
  company,
  fakeApi,
  json,
  lead,
  leadListRoutes,
  outreachContext,
  page,
  session,
  worklistItem,
  type RecordedCall,
} from '../../test/fakeApi';
import { LeadDetailPage } from './LeadDetailPage';
import { LeadsPage } from './LeadsPage';
import { EMPTY_FILTERS, filtersFromQuery, filtersToQuery } from './worklistFilters';

/** Fila de leads, resumo, lote, visões e detalhe (D31). Dados fictícios. */

const OTHER_ID = '33333333-3333-4333-8333-333333333333';
const THIRD_ID = '44444444-4444-4444-8444-444444444444';

const items = [
  {
    ...worklistItem,
    estimatedValue: 2500,
    assigneeName: 'Operadora Ficticia',
    nextTaskTitle: 'Ligar de novo',
    nextTaskDueAt: '2026-01-01T12:00:00Z',
    stageEnteredAt: '2026-01-01T12:00:00Z',
  },
  { ...worklistItem, leadId: OTHER_ID, companyName: 'Padaria Ficticia', segment: undefined },
  { ...worklistItem, leadId: THIRD_ID, companyName: 'Oficina Ficticia' },
];

function listRoutes(overrides: Readonly<Record<string, (call: RecordedCall) => Response>> = {}): RecordedCall[] {
  return fakeApi({
    ...leadListRoutes(items),
    'GET /companies/segments': () => json({ segments: [{ name: 'Barbearia', companies: 3 }], withoutSegment: 2 }),
    'GET /team': () => json(page([{ userId: 'u-2', displayName: 'Operadora Ficticia' }])),
    [`GET /leads/${LEAD_ID}/summary`]: () => json(items[0]),
    [`GET /leads/${LEAD_ID}/timeline`]: () =>
      json(page([{ type: 'STATUS_CHANGE', id: 'e-1', occurredAt: '2026-11-01T12:00:00Z', toStatus: 'NEW' }])),
    'GET /companies/': () => json({ ...company, phone: '(35) 3333-4444', businessEmail: 'contato@loja.example.test' }),
    ...overrides,
  });
}

function lastWorklist(calls: readonly RecordedCall[]): URLSearchParams | undefined {
  return calls.filter((call) => call.path === '/leads/worklist').at(-1)?.query;
}

describe('filtros da fila na URL', () => {
  it('ida e volta sem perder nada e sem o que e padrao', () => {
    const filters = { ...EMPTY_FILTERS, status: 'WON' as const, noSegment: true, sort: 'VALUE' as const };
    const query = filtersToQuery(filters);
    expect(query).toBe('status=WON&noSegment=true&sort=VALUE');
    expect(filtersFromQuery(query)).toEqual(filters);
  });

  it('valor desconhecido e ignorado, nunca vira filtro', () => {
    const filters = filtersFromQuery('status=XYZ&priority=HIGH&scoreMin=900&sort=HACK&direction=up&source=CSV_IMPORT');
    expect(filters.status).toBe('');
    expect(filters.priority).toBe('HIGH');
    expect(filters.scoreMin).toBe('');
    expect(filters.sort).toBe('UPDATED');
    expect(filters.direction).toBe('desc');
    expect(filters.source).toBe('CSV_IMPORT');
  });
});

describe('Lista de leads', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/leads?status=PROPOSAL_SENT&source=CSV_IMPORT&priority=XYZ');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.replaceState(null, '', '/');
    window.sessionStorage.clear();
  });

  it('abre filtrada pela URL e mostra o que fazer com cada lead', async () => {
    const calls = listRoutes();
    render(<LeadsPage session={session} />);

    const table = await screen.findByRole('table', { name: 'Leads' });
    const query = lastWorklist(calls);
    expect(query?.get('status')).toBe('PROPOSAL_SENT');
    expect(query?.get('source')).toBe('CSV_IMPORT');
    expect(query?.get('priority')).toBeNull();
    const row = within(table).getByRole('button', { name: 'Barbearia Ficticia' }).closest('tr');
    expect(row).toHaveTextContent('Operadora Ficticia');
    expect(row).toHaveTextContent('Ligar de novo');
    expect(row).toHaveTextContent(/Atrasada/);
    expect(row).toHaveTextContent(/2\.500,00/);
    expect(within(table).getByRole('link', { name: 'Abrir Barbearia Ficticia' })).toHaveAttribute(
      'href',
      `/leads/${LEAD_ID}`,
    );
  });

  it('recortes rapidos, ordenacao e segmento vao para a fila e para a URL', async () => {
    const calls = listRoutes();
    render(<LeadsPage session={session} />);
    await screen.findByRole('table', { name: 'Leads' });

    await userEvent.click(screen.getByRole('button', { name: 'Tarefa atrasada' }));
    await waitFor(() => {
      expect(lastWorklist(calls)?.get('focus')).toBe('OVERDUE');
    });
    expect(new URLSearchParams(window.location.search).get('focus')).toBe('OVERDUE');
    await userEvent.click(screen.getByRole('button', { name: 'Meus' }));
    await waitFor(() => {
      expect(lastWorklist(calls)?.get('assignee')).toBe(session.userId);
    });
    expect(lastWorklist(calls)?.get('focus')).toBeNull();

    const valor = screen.getByRole('button', { name: 'Valor' });
    await userEvent.click(valor);
    await waitFor(() => {
      expect(lastWorklist(calls)?.get('sort')).toBe('VALUE');
    });
    expect(lastWorklist(calls)?.get('direction')).toBe('desc');
    expect(valor.closest('th')).toHaveAttribute('aria-sort', 'descending');
    await userEvent.click(screen.getByRole('button', { name: 'Valor' }));
    await waitFor(() => {
      expect(lastWorklist(calls)?.get('direction')).toBe('asc');
    });

    const segmento = screen.getByLabelText('Segmento');
    expect(within(segmento).getByRole('option', { name: 'Barbearia (3)' })).toBeInTheDocument();
    await userEvent.selectOptions(segmento, 'Sem segmento (2)');
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));
    await waitFor(() => {
      expect(lastWorklist(calls)?.get('noSegment')).toBe('true');
    });
    expect(lastWorklist(calls)?.get('segment')).toBeNull();
  });

  it('filtros secundarios ficam em Mais filtros, aberto quando a URL ja traz um', async () => {
    listRoutes();
    const { unmount } = render(<LeadsPage session={session} />);
    // A URL do teste traz origem: Mais filtros abre sozinho e conta 1.
    const mais = await screen.findByRole('button', { name: /Mais filtros/ });
    expect(mais).toHaveAttribute('aria-expanded', 'true');
    expect(mais).toHaveTextContent('1');
    expect(screen.getByLabelText('Origem')).toHaveValue('CSV_IMPORT');
    unmount();

    window.history.replaceState(null, '', '/leads');
    render(<LeadsPage session={session} />);
    const fechado = await screen.findByRole('button', { name: /Mais filtros/ });
    expect(fechado).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByLabelText('Prioridade')).not.toBeInTheDocument();
    await userEvent.click(fechado);
    expect(screen.getByLabelText('Prioridade')).toBeInTheDocument();
  });

  it('clicar no lead abre o resumo ao lado; Esc fecha', async () => {
    listRoutes();
    render(<LeadsPage session={session} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Barbearia Ficticia' }));
    const resumo = await screen.findByRole('complementary', { name: 'Resumo do lead' });
    expect(await within(resumo).findByRole('heading', { name: 'Barbearia Ficticia' })).toHaveFocus();
    expect(resumo).toHaveTextContent('Operadora Ficticia');
    expect(resumo).toHaveTextContent(/Atrasada/);
    expect(await within(resumo).findByRole('link', { name: '(35) 3333-4444' })).toHaveAttribute(
      'href',
      'tel:3533334444',
    );
    expect(await within(resumo).findByText('Lead aberto como Novo')).toBeInTheDocument();
    expect(within(resumo).getByRole('link', { name: 'Abrir lead' })).toHaveAttribute('href', `/leads/${LEAD_ID}`);

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('complementary', { name: 'Resumo do lead' })).not.toBeInTheDocument();
  });

  it('no celular, tocar fora do resumo fecha sem abrir o lead que esta embaixo', async () => {
    const calls = listRoutes();
    render(<LeadsPage session={session} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Barbearia Ficticia' }));
    await screen.findByRole('complementary', { name: 'Resumo do lead' });
    const summaries = (): number => calls.filter((call) => call.path.endsWith('/summary')).length;
    const before = summaries();

    // O fundo cobre a lista: o toque fecha o resumo e não chega à linha embaixo.
    await userEvent.click(screen.getByTestId('fundo-resumo'));
    expect(screen.queryByRole('complementary', { name: 'Resumo do lead' })).not.toBeInTheDocument();
    expect(screen.queryByTestId('fundo-resumo')).not.toBeInTheDocument();
    expect(summaries()).toBe(before);
  });

  it('acao em lote atribui os selecionados de uma vez', async () => {
    const calls = listRoutes({ 'POST /leads/bulk': () => json({ updated: 2 }) });
    render(<LeadsPage session={session} />);
    await screen.findByRole('table', { name: 'Leads' });

    await userEvent.click(screen.getByLabelText('Selecionar Barbearia Ficticia'));
    await userEvent.click(screen.getByLabelText('Selecionar Padaria Ficticia'));
    const lote = screen.getByRole('region', { name: 'Ações em lote' });
    expect(lote).toHaveTextContent('2 selecionados');
    await userEvent.selectOptions(
      within(lote).getByLabelText('Responsável para os selecionados'),
      'Operadora Ficticia',
    );
    await userEvent.click(within(lote).getByRole('button', { name: 'Atribuir' }));

    expect(await screen.findByText('2 leads atualizados.')).toBeInTheDocument();
    expect(calls.find((call) => call.path === '/leads/bulk')?.body).toEqual({
      leadIds: [LEAD_ID, OTHER_ID],
      action: 'ASSIGN',
      assignedUserId: 'u-2',
    });
    expect(screen.queryByRole('region', { name: 'Ações em lote' })).not.toBeInTheDocument();
  });

  it('visoes salvas: salvar a busca atual, abrir e apagar com confirmacao', async () => {
    const views = [
      {
        id: 'v-1',
        name: 'Minhas propostas',
        query: 'status=PROPOSAL_SENT',
        shared: false,
        canDelete: true,
        createdAt: '2026-11-01T12:00:00Z',
      },
    ];
    const calls = listRoutes({
      'GET /leads/views': () => json(views),
      'POST /leads/views': () =>
        json({ id: 'v-2', name: 'Importadas', query: '', createdAt: '2026-11-06T12:00:00Z' }, 201),
      'DELETE /leads/views/': () => new Response(null, { status: 204 }),
    });
    render(<LeadsPage session={session} />);

    const salvas = await screen.findByRole('list', { name: 'Visões salvas' });
    expect(within(salvas).getByRole('link', { name: 'Minhas propostas' })).toHaveAttribute(
      'href',
      '/leads?status=PROPOSAL_SENT',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Salvar esta visão' }));
    await userEvent.type(screen.getByLabelText('Nome da visão'), 'Importadas');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => {
      expect(calls.find((call) => call.method === 'POST' && call.path === '/leads/views')?.body).toEqual({
        name: 'Importadas',
        query: 'status=PROPOSAL_SENT&source=CSV_IMPORT',
        shared: false,
      });
    });

    await userEvent.click(within(salvas).getByRole('button', { name: 'Apagar a visão Minhas propostas' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Apagar' }));
    await waitFor(() => {
      expect(calls.some((call) => call.method === 'DELETE' && call.path === '/leads/views/v-1')).toBe(true);
    });
  });

  it('visao da equipe aparece sem apagar para operador; administrador pode compartilhar', async () => {
    const teamView = {
      id: 'v-9',
      name: 'Propostas da semana',
      query: 'status=PROPOSAL_SENT',
      shared: true,
      canDelete: false,
      createdAt: '2026-11-01T12:00:00Z',
    };
    const calls = listRoutes({
      'GET /leads/views': () => json([teamView]),
      'POST /leads/views': () => json({ ...teamView, id: 'v-10', name: 'Da equipe', canDelete: true }, 201),
    });
    const { unmount } = render(<LeadsPage session={{ ...session, role: 'OPERATOR' }} />);

    const salvas = await screen.findByRole('list', { name: 'Visões salvas' });
    expect(within(salvas).getByRole('link', { name: 'Propostas da semana (da equipe)' })).toBeInTheDocument();
    expect(within(salvas).queryByRole('button', { name: /Apagar a visão/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Salvar esta visão' }));
    expect(screen.queryByLabelText('Compartilhar com a equipe')).not.toBeInTheDocument();
    unmount();

    render(<LeadsPage session={session} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Salvar esta visão' }));
    await userEvent.type(screen.getByLabelText('Nome da visão'), 'Da equipe');
    await userEvent.click(screen.getByLabelText('Compartilhar com a equipe'));
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));
    await waitFor(() => {
      expect(calls.find((call) => call.method === 'POST' && call.path === '/leads/views')?.body).toMatchObject({
        name: 'Da equipe',
        shared: true,
      });
    });
  });

  it('filtrar grava a busca na URL e guarda a ordem para o anterior/proximo do detalhe', async () => {
    listRoutes();
    render(<LeadsPage session={session} />);
    await screen.findByRole('table', { name: 'Leads' });

    fireEvent.change(screen.getByLabelText('Buscar empresa'), { target: { value: 'Barbearia' } });
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));

    expect(new URLSearchParams(window.location.search).get('search')).toBe('Barbearia');
    const saved: unknown = JSON.parse(window.sessionStorage.getItem('jh-prospect:leads.navigation') ?? 'null');
    expect(saved).toMatchObject({ ids: [LEAD_ID, OTHER_ID, THIRD_ID] });
  });
});

/** Rotas do detalhe: todas as seções precisam da sua, porque a API simulada casa por prefixo. */
function detailRoutes(patchResponse: (call: RecordedCall) => Response = () => json(lead)) {
  return fakeApi({
    'GET /leads/': () => json(lead),
    [`GET /leads/${LEAD_ID}/summary`]: () => json(items[0]),
    [`GET /leads/${LEAD_ID}/analyses`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/tasks`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(outreachContext),
    [`GET /leads/${LEAD_ID}/messages`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/timeline`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/deal`]: () => json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404),
    'GET /team': () => json(page([{ userId: 'u-2', displayName: 'Operadora Ficticia' }])),
    'PATCH /leads/': patchResponse,
  });
}

describe('Detalhe do lead: topo, abas e navegacao', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.sessionStorage.clear();
    window.history.replaceState(null, '', '/');
  });

  it('mostra o essencial no topo, sem rolar', async () => {
    detailRoutes();
    render(<LeadDetailPage id={LEAD_ID} />);

    const resumo = await screen.findByLabelText('Resumo do lead');
    await waitFor(() => {
      expect(resumo).toHaveTextContent(/2\.500,00/);
    });
    expect(resumo).toHaveTextContent('Operadora Ficticia');
    expect(resumo).toHaveTextContent(/Atrasada: Ligar de novo/);
    expect(screen.getByRole('link', { name: 'Ver empresa' })).toHaveAttribute('href', `/companies/${COMPANY_ID}`);
    expect(screen.getByRole('tab', { name: 'Histórico' })).toHaveAttribute('aria-selected', 'true');
  });

  it('anterior/proximo seguem a lista trabalhada; j avanca e n vai para a interacao', async () => {
    window.sessionStorage.setItem(
      'jh-prospect:leads.navigation',
      JSON.stringify({ ids: [OTHER_ID, LEAD_ID, THIRD_ID], query: 'status=NEW' }),
    );
    detailRoutes();
    render(<LeadDetailPage id={LEAD_ID} />);

    const nav = await screen.findByRole('navigation', { name: 'Navegação entre leads' });
    expect(nav).toHaveTextContent('2 de 3 na lista');
    expect(within(nav).getByRole('link', { name: /Lead anterior/ })).toHaveAttribute('href', `/leads/${OTHER_ID}`);
    expect(within(nav).getByRole('link', { name: 'Voltar para a lista' })).toHaveAttribute('href', '/leads?status=NEW');

    await userEvent.click(screen.getByRole('tab', { name: 'Mensagens' }));
    await userEvent.keyboard('n');
    expect(screen.getByRole('tab', { name: 'Histórico' })).toHaveAttribute('aria-selected', 'true');

    // Com o foco num campo, a letra é texto: o atalho não dispara.
    await waitFor(() => {
      expect(document.activeElement?.tagName).toMatch(/SELECT|INPUT/);
    });
    await userEvent.keyboard('j');
    expect(window.location.pathname).toBe('/');

    await userEvent.click(document.body);
    await userEvent.keyboard('j');
    expect(window.location.pathname).toBe(`/leads/${THIRD_ID}`);
  });

  it('abas trocam pelas setas, como no padrao de abas', async () => {
    detailRoutes();
    render(<LeadDetailPage id={LEAD_ID} />);

    const historico = await screen.findByRole('tab', { name: 'Histórico' });
    historico.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Análise' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Análise' })).toHaveFocus();
  });
});

describe('Responsavel pelo lead', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('atribui um membro da equipe e mostra quem e', async () => {
    const calls = detailRoutes(() => json({ ...lead, assignedUserId: 'u-2' }));
    render(<LeadDetailPage id={LEAD_ID} />);

    const select = await screen.findByLabelText('Responsável pelo lead');
    await waitFor(() => {
      expect(within(select).getByRole('option', { name: 'Operadora Ficticia' })).toBeInTheDocument();
    });
    expect(select).toHaveValue('');
    await userEvent.selectOptions(select, 'Operadora Ficticia');

    await waitFor(() => {
      expect(select).toHaveValue('u-2');
    });
    const patch = calls.find((call) => call.method === 'PATCH');
    expect(patch?.path).toBe(`/leads/${LEAD_ID}`);
    expect(patch?.body).toEqual({ assignedUserId: 'u-2' });
  });

  it('membro que deixou de estar ativo recebe aviso claro', async () => {
    detailRoutes(() => json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404));
    render(<LeadDetailPage id={LEAD_ID} />);

    const select = await screen.findByLabelText('Responsável pelo lead');
    await waitFor(() => {
      expect(within(select).getByRole('option', { name: 'Operadora Ficticia' })).toBeInTheDocument();
    });
    await userEvent.selectOptions(select, 'Operadora Ficticia');

    expect(await screen.findByText(/não é mais membro ativo/)).toBeInTheDocument();
  });
});
