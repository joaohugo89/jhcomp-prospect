import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, page, type RecordedCall } from '../../test/fakeApi';
import { DashboardPage } from './DashboardPage';
import { DashboardPreviewPage } from './DashboardPreviewPage';
import { periodoAnterior, toAtividade, toConversao, toDashboardData, variacao } from './model/dashboardData';
import { mockDashboardData } from './model/mockDashboardData';

/** Dashboard: modelo de tela, pré-visualização e página com a API simulada. Dados fictícios. */

const summary = {
  from: '2026-10-07',
  to: '2026-11-05',
  summary: {
    leadsByStatus: { NEW: 3, QUALIFIED: 1, WON: 1 },
    activeLeads: 4,
    companiesAdded: 5,
    contactsRegistered: 2,
    openProposals: 1,
    dealsWon: 1,
    wonValue: 2500,
    openTasks: 4,
    overdueTasks: 1,
  },
};

/** Período anterior: base da variação (2.000 → 2.500 = +25%). */
const previousSummary = {
  ...summary,
  summary: { ...summary.summary, wonValue: 2000, companiesAdded: 0 },
};

const timeline = {
  from: '2026-10-07',
  to: '2026-11-05',
  granularity: 'WEEK',
  points: [
    { start: '2026-10-07', companiesAdded: 2, contactsRegistered: 0, dealsWon: 0, wonValue: 0 },
    { start: '2026-10-12', companiesAdded: 0, contactsRegistered: 1, dealsWon: 1, wonValue: 2500 },
    { start: '2026-10-19', companiesAdded: 3, contactsRegistered: 1, dealsWon: 0, wonValue: 0 },
  ],
};

const conversion = {
  from: '2026-10-07',
  to: '2026-11-05',
  conversion: {
    minimumSample: 10,
    bySource: [
      { key: 'CSV_IMPORT', leads: 20, contacted: 5, won: 2, sufficientSample: true },
      { key: 'MANUAL', leads: 4, contacted: 1, won: 1, sufficientSample: false },
    ],
    bySegment: [{ leads: 24, contacted: 6, won: 3, sufficientSample: true }],
  },
};

const activity = page([
  {
    type: 'STATUS_CHANGE',
    leadId: LEAD_ID,
    companyName: 'Barbearia Ficticia',
    occurredAt: '2026-11-04T12:00:00Z',
    fromStatus: 'PROPOSAL_SENT',
    toStatus: 'WON',
    actorName: 'Operadora',
  },
]);

/** Rotas do dashboard; o resumo do período anterior responde `previousSummary`. */
function routes(overrides: Partial<Record<string, (call: RecordedCall) => Response>> = {}): RecordedCall[] {
  return fakeApi({
    'GET /dashboard/summary': (call) => json(pick(call)),
    'GET /dashboard/timeline': () => json(timeline),
    'GET /dashboard/conversion': () => json(conversion),
    'GET /dashboard/activity': () => json(activity),
    ...overrides,
  });
}

function pick(call: RecordedCall): unknown {
  const today = new Date();
  const from = call.query.get('from') ?? '';
  // Início mais de 30 dias atrás: é o período anterior.
  return Date.parse(`${from}T00:00:00`) < today.getTime() - 31 * 86_400_000 ? previousSummary : summary;
}

/** Promessa liberada pelo teste: simula uma resposta que ainda não chegou. */
function deferred(): { readonly promise: Promise<void>; readonly resolve: () => void } {
  let done: (() => void) | undefined;
  const promise = new Promise<void>((resolve) => {
    done = resolve;
  });
  return {
    promise,
    resolve: () => {
      done?.();
    },
  };
}

describe('modelo do dashboard', () => {
  it('converte o resumo da API com todas as etapas, inclusive as vazias', () => {
    const data = toDashboardData(summary);
    expect(data.periodo).toEqual({ de: '2026-10-07', ate: '2026-11-05' });
    expect(data.metricasAtuais.tarefasAtrasadas).toBe(1);
    expect(data.leadsPorStatus).toHaveLength(9);
    expect(data.leadsPorStatus[0]).toEqual({ status: 'NEW', nome: 'Novo', quantidade: 3 });
    expect(data.leadsPorStatus.find((item) => item.status === 'LOST')?.quantidade).toBe(0);
  });

  it('converte atividade de status e de interacao, sem campo vazio', () => {
    const base = { leadId: LEAD_ID, companyName: 'Loja Ficticia', occurredAt: '2026-11-06T12:00:00Z' };
    expect(toAtividade({ ...base, type: 'STATUS_CHANGE', fromStatus: 'NEW', toStatus: 'QUALIFIED' }).transicao).toEqual(
      {
        tipo: 'status',
        de: 'NEW',
        para: 'QUALIFIED',
      },
    );
    const interacao = toAtividade({ ...base, type: 'INTERACTION', interactionType: 'CONTACT', channel: 'PHONE' });
    expect(interacao.transicao).toEqual({ tipo: 'interacao', interacao: 'CONTACT', canal: 'PHONE' });
    expect('usuario' in interacao).toBe(false);
  });

  it('soma a conversao das origens e so marca amostra suficiente a partir do minimo', () => {
    const conversao = toConversao(conversion);
    expect(conversao.total).toEqual({ leads: 24, contatados: 6, ganhos: 3, amostraSuficiente: true });
    expect(conversao.porOrigem.map((grupo) => grupo.nome)).toEqual(['Importação CSV', 'Manual']);
    expect(conversao.porSegmento[0]?.nome).toBe('Sem segmento');
  });

  it('variacao: percentual com base, sem percentual quando antes era zero', () => {
    expect(variacao(2500, 2000)).toEqual({ tipo: 'percentual', valor: 25 });
    expect(variacao(5, 0)).toEqual({ tipo: 'semBase' });
    expect(variacao(0, 0)).toEqual({ tipo: 'semDados' });
    expect(variacao(5, undefined)).toEqual({ tipo: 'semDados' });
  });

  it('periodo anterior tem o mesmo tamanho e termina na vespera', () => {
    expect(periodoAnterior({ de: '2026-10-08', ate: '2026-11-06' })).toEqual({ de: '2026-09-08', ate: '2026-10-07' });
    expect(periodoAnterior({ de: '2026-03-01', ate: '2026-03-01' })).toEqual({ de: '2026-02-28', ate: '2026-02-28' });
  });
});

describe('pre-visualizacao com mockDashboardData', () => {
  it('monta o layout inteiro sem chamar a API', () => {
    const spy = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', spy);
    render(<DashboardPreviewPage />);

    expect(screen.getByText('Pré-visualização')).toBeInTheDocument();
    const metricas = screen.getByRole('region', { name: 'Métricas-chave' });
    expect(within(metricas).getByText('Empresas').nextElementSibling).toHaveTextContent(
      String(mockDashboardData.metricasPeriodo.empresasAdicionadas),
    );
    expect(screen.getByText('Atenção')).toBeInTheDocument();
    expect(screen.getByText('Barbearia Exemplo')).toBeInTheDocument();
    expect(spy).not.toHaveBeenCalled();
    vi.unstubAllGlobals();
  });
});

describe('Dashboard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mostra destaque com variacao, conversao, origem, funil e atividade', async () => {
    const calls = routes();
    render(<DashboardPage />);

    const hero = await screen.findByRole('region', { name: 'Valor ganho' });
    expect(within(hero).getAllByText(/2\.500,00/).length).toBeGreaterThan(0);
    expect(await within(hero).findByText('+25%')).toBeInTheDocument();
    expect(within(hero).getByText('24 leads abertos no período')).toBeInTheDocument();
    // Ticket médio = valor ganho / negócios ganhos.
    expect(within(hero).getByText('Ticket médio').nextElementSibling).toHaveTextContent(/R\$\s?2,5\s?mil/);

    // 3 ganhos em 24 leads, amostra suficiente: há percentual.
    const gauge = screen.getByRole('region', { name: 'Taxa de conversão' });
    expect(within(gauge).getByText('12,5%')).toBeInTheDocument();
    expect(within(gauge).getByText('3 de 24 leads')).toBeInTheDocument();

    const origem = screen.getByRole('region', { name: 'Origem dos leads' });
    expect(within(origem).getByText('Importação CSV')).toBeInTheDocument();
    expect(within(origem).getByText('(83%)')).toBeInTheDocument();

    const funil = screen.getByRole('region', { name: 'Funil · status dos leads' });
    expect(within(funil).getByText('Novo').closest('div')).toHaveTextContent('3(60%)');
    expect(within(funil).getByText('Perdido: 0')).toBeInTheDocument();

    expect(screen.getByText('Atrasadas').closest('div')).toHaveTextContent('Atenção');

    const atividades = screen.getByRole('region', { name: 'Interações e atividades' });
    const item = await within(atividades).findByRole('listitem');
    expect(calls.find((call) => call.path === '/dashboard/activity')?.query.get('size')).toBe('6');
    expect(item).toHaveTextContent('Barbearia Ficticia');
    expect(item).toHaveTextContent('Proposta enviada → Ganho · Operadora');
  });

  it('sem amostra suficiente a taxa nao mostra percentual', async () => {
    routes({
      'GET /dashboard/conversion': () =>
        json({
          ...conversion,
          conversion: {
            minimumSample: 10,
            bySource: [{ key: 'MANUAL', leads: 4, contacted: 1, won: 1, sufficientSample: false }],
            bySegment: [],
          },
        }),
    });
    render(<DashboardPage />);

    const gauge = await screen.findByRole('region', { name: 'Taxa de conversão' });
    expect(await within(gauge).findByText('1 de 4 leads · amostra pequena (mínimo 10)')).toBeInTheDocument();
    expect(within(gauge).queryByText(/%/)).not.toBeInTheDocument();
  });

  it('grafico troca de metrica e percorre os intervalos pelo teclado', async () => {
    const calls = routes();
    render(<DashboardPage />);

    await screen.findByRole('region', { name: 'Valor ganho' });
    await userEvent.click(screen.getByRole('button', { name: 'Empresas adicionadas' }));
    const hero = screen.getByRole('region', { name: 'Empresas adicionadas' });
    expect(within(hero).getByRole('row', { name: /Semana a partir de 19\/10\/2026/ })).toHaveTextContent('3');
    // Sem base no período anterior (0 empresas), não há percentual.
    expect(within(hero).getByText('sem registro no período anterior')).toBeInTheDocument();

    const chart = within(hero).getByRole('slider', { name: /Empresas adicionadas por semana/ });
    chart.focus();
    await userEvent.keyboard('{Home}');
    expect(chart).toHaveAttribute('aria-valuetext', 'Semana a partir de 07/10/2026: 2');

    await userEvent.selectOptions(within(hero).getByLabelText('Agrupar por'), 'MONTH');
    await waitFor(() => {
      expect(
        calls
          .filter((call) => call.path === '/dashboard/timeline')
          .at(-1)
          ?.query.get('granularity'),
      ).toBe('MONTH');
    });
  });

  it('painel de filtros envia segmento, cidade, origem e recusa inicio depois do fim', async () => {
    const calls = routes();
    render(<DashboardPage />);
    await screen.findByRole('region', { name: 'Valor ganho' });

    await userEvent.click(screen.getByRole('button', { name: /Filtros/ }));
    const painel = screen.getByRole('form', { name: 'Filtros' });
    fireEvent.change(within(painel).getByLabelText('De'), { target: { value: '2026-11-10' } });
    fireEvent.change(within(painel).getByLabelText('Até'), { target: { value: '2026-11-01' } });
    expect(within(painel).getByRole('button', { name: 'Aplicar' })).toBeDisabled();
    expect(within(painel).getByRole('alert')).toHaveTextContent('A data inicial deve ser anterior à final.');

    fireEvent.change(within(painel).getByLabelText('De'), { target: { value: '2026-10-01' } });
    fireEvent.change(within(painel).getByLabelText('Segmento'), { target: { value: ' BARBERSHOP ' } });
    fireEvent.change(within(painel).getByLabelText('Cidade'), { target: { value: 'Cidade Exemplo' } });
    await userEvent.selectOptions(within(painel).getByLabelText('Origem'), 'CSV_IMPORT');
    await userEvent.click(within(painel).getByRole('button', { name: 'Aplicar' }));

    await waitFor(() => {
      for (const path of [
        '/dashboard/summary',
        '/dashboard/conversion',
        '/dashboard/activity',
        '/dashboard/timeline',
      ]) {
        const last = calls.filter((call) => call.path === path && call.query.get('to') === '2026-11-01').at(-1);
        expect(last?.query.get('from')).toBe('2026-10-01');
        expect(last?.query.get('segment')).toBe('BARBERSHOP');
        expect(last?.query.get('city')).toBe('Cidade Exemplo');
        expect(last?.query.get('source')).toBe('CSV_IMPORT');
      }
    });
    expect(screen.queryByRole('form', { name: 'Filtros' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Filtros/ })).toHaveTextContent('3');
    expect(screen.getByLabelText('Período')).toHaveValue('custom');
  });

  it('atalho de periodo aplica na hora e Esc fecha o painel', async () => {
    const calls = routes();
    render(<DashboardPage />);
    await screen.findByRole('region', { name: 'Valor ganho' });
    expect(screen.getByLabelText('Período')).toHaveValue('30');

    await userEvent.selectOptions(screen.getByLabelText('Período'), '7');
    await waitFor(() => {
      const last = calls.filter((call) => call.path === '/dashboard/summary').at(-1);
      const from = Date.parse(`${last?.query.get('from') ?? ''}T00:00:00Z`);
      const to = Date.parse(`${last?.query.get('to') ?? ''}T00:00:00Z`);
      expect(Math.round((to - from) / 86_400_000)).toBe(6);
    });

    const toggle = screen.getByRole('button', { name: /Filtros/ });
    await userEvent.click(toggle);
    expect(screen.getByRole('form', { name: 'Filtros' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('form', { name: 'Filtros' })).not.toBeInTheDocument();
    expect(toggle).toHaveFocus();
  });

  it('relatorio de conversao expande com as tabelas por origem e segmento', async () => {
    routes();
    render(<DashboardPage />);

    const botao = await screen.findByRole('button', { name: /Expandir relatório de conversão/ });
    expect(botao).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(botao);
    expect(botao).toHaveAttribute('aria-expanded', 'true');

    const porOrigem = screen.getByRole('table', { name: 'Por origem' });
    expect(within(porOrigem).getByRole('row', { name: /Importação CSV/ })).toHaveTextContent('2 (10%)');
    expect(within(porOrigem).getByRole('row', { name: /Manual/ })).toHaveTextContent('amostra pequena');
    expect(screen.getByRole('table', { name: 'Por segmento' })).toBeInTheDocument();
  });

  it('trocar de pagina da atividade mantem a lista e o foco ate a nova chegar', async () => {
    const item = activity.content[0];
    const calls = routes({
      'GET /dashboard/activity': (call) =>
        call.query.get('page') === '1'
          ? json({
              ...page([{ ...item, companyName: 'Loja Pagina Dois' }], { page: 1, totalPages: 2 }),
              totalElements: 7,
            })
          : json({
              ...page([{ ...item, companyName: 'Loja Pagina Um' }], { totalPages: 2, hasNext: true }),
              totalElements: 7,
            }),
    });
    // Segura a resposta da página 2 até o teste liberar, como numa rede lenta.
    const { promise: held, resolve: release } = deferred();
    const fakeFetch = globalThis.fetch;
    vi.stubGlobal('fetch', async (url: string, init?: RequestInit) => {
      if (url.includes('/dashboard/activity') && url.includes('page=1')) {
        await held;
      }
      return fakeFetch(url, init);
    });
    render(<DashboardPage />);

    const atividades = await screen.findByRole('region', { name: 'Interações e atividades' });
    await within(atividades).findByText('Loja Pagina Um');
    const proxima = within(atividades).getByRole('button', { name: 'Próxima' });
    await userEvent.click(proxima);

    // Enquanto a página 2 carrega, a 1 continua na tela e o botão segue com o foco.
    expect(atividades).toHaveAttribute('aria-busy', 'true');
    expect(within(atividades).getByText('Loja Pagina Um')).toBeInTheDocument();
    expect(proxima).toHaveFocus();

    release();
    expect(await within(atividades).findByText('Loja Pagina Dois')).toBeInTheDocument();
    expect(atividades).toHaveAttribute('aria-busy', 'false');
    expect(calls.some((call) => call.path === '/dashboard/activity' && call.query.get('page') === '1')).toBe(true);
  });
});
