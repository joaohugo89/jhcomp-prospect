import { SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../../app/labels';
import { SOURCE_TYPES } from '../../../types/company';
import type {
  Activity,
  DashboardConversion,
  DashboardSummary,
  DashboardTimeline,
  Granularity,
  InteractionChannel,
  InteractionType,
} from '../../../types/crm';
import { LEAD_STATUSES, type LeadStatus } from '../../../types/lead';

/**
 * Modelo de tela do dashboard.
 *
 * Os componentes de apresentação recebem só estes tipos, nunca a resposta da
 * API: o contrato da API (em inglês, doc 05) pode evoluir sem tocar no layout,
 * e o mesmo layout roda com `mockDashboardData` em testes e na pré-visualização.
 * A conversão fica toda neste arquivo.
 */

/** Datas de calendário (AAAA-MM-DD), inclusivas nas duas pontas. */
export interface PeriodoSelecionado {
  readonly de: string;
  readonly ate: string;
}

/** Eventos dentro do período selecionado. */
export interface MetricasPeriodo {
  readonly empresasAdicionadas: number;
  readonly contatosRegistrados: number;
  readonly negociosGanhos: number;
  /** Em reais. */
  readonly valorGanho: number;
}

/** Situação de agora, independente do período. */
export interface MetricasAtuais {
  readonly leadsAtivos: number;
  readonly propostasEmAberto: number;
  readonly tarefasAbertas: number;
  readonly tarefasAtrasadas: number;
}

export interface StatusLead {
  readonly status: LeadStatus;
  readonly nome: string;
  readonly quantidade: number;
}

/** O que aconteceu: mudança de etapa (sem `de` é a abertura do lead) ou interação. */
export type Transicao =
  | { readonly tipo: 'status'; readonly de?: LeadStatus; readonly para?: LeadStatus }
  | { readonly tipo: 'interacao'; readonly interacao?: InteractionType; readonly canal?: InteractionChannel };

export interface AtividadeRecente {
  readonly id: string;
  readonly leadId: string;
  readonly empresa: string;
  readonly transicao: Transicao;
  /** Instante ISO-8601 em UTC. */
  readonly data: string;
  readonly usuario?: string;
}

/** Tudo o que o resumo do dashboard mostra. A atividade é paginada e vem à parte. */
export interface DashboardData {
  readonly periodo: PeriodoSelecionado;
  readonly metricasPeriodo: MetricasPeriodo;
  readonly metricasAtuais: MetricasAtuais;
  /** Todas as etapas, na ordem do funil, inclusive as vazias. */
  readonly leadsPorStatus: readonly StatusLead[];
}

export function toDashboardData(response: DashboardSummary): DashboardData {
  const { summary } = response;
  return {
    periodo: { de: response.from, ate: response.to },
    metricasPeriodo: {
      empresasAdicionadas: summary.companiesAdded,
      contatosRegistrados: summary.contactsRegistered,
      negociosGanhos: summary.dealsWon,
      valorGanho: summary.wonValue,
    },
    metricasAtuais: {
      leadsAtivos: summary.activeLeads,
      propostasEmAberto: summary.openProposals,
      tarefasAbertas: summary.openTasks,
      tarefasAtrasadas: summary.overdueTasks,
    },
    leadsPorStatus: LEAD_STATUSES.map((status) => ({
      status,
      nome: STATUS_LABEL[status],
      quantidade: summary.leadsByStatus[status] ?? 0,
    })),
  };
}

export function toAtividade(activity: Activity): AtividadeRecente {
  const { interactionType, channel, fromStatus, toStatus, actorName } = activity;
  const transicao: Transicao =
    activity.type === 'INTERACTION'
      ? {
          tipo: 'interacao',
          ...(interactionType === undefined ? {} : { interacao: interactionType }),
          ...(channel === undefined ? {} : { canal: channel }),
        }
      : {
          tipo: 'status',
          ...(fromStatus === undefined ? {} : { de: fromStatus }),
          ...(toStatus === undefined ? {} : { para: toStatus }),
        };
  return {
    // A API não dá id ao evento; tipo, lead e instante o identificam na lista.
    id: `${activity.type}-${activity.leadId}-${activity.occurredAt}`,
    leadId: activity.leadId,
    empresa: activity.companyName,
    transicao,
    data: activity.occurredAt,
    // Campo opcional só quando há valor (exactOptionalPropertyTypes).
    ...(actorName === undefined ? {} : { usuario: actorName }),
  };
}

/** Um intervalo da série; `inicio` é o primeiro dia dele dentro do período. */
export interface PontoSerie extends MetricasPeriodo {
  readonly inicio: string;
}

export interface SerieTemporal {
  readonly granularidade: Granularity;
  readonly pontos: readonly PontoSerie[];
}

export function toSerie(response: DashboardTimeline): SerieTemporal {
  return {
    granularidade: response.granularity,
    pontos: response.points.map((point) => ({
      inicio: point.start,
      empresasAdicionadas: point.companiesAdded,
      contatosRegistrados: point.contactsRegistered,
      negociosGanhos: point.dealsWon,
      valorGanho: point.wonValue,
    })),
  };
}

/** Leads abertos no período de um grupo (origem ou segmento) e o que deles resultou (RF-071). */
export interface GrupoConversao {
  /** Ausente: segmento não informado ou origem não registrada. */
  readonly chave?: string;
  readonly nome: string;
  readonly leads: number;
  readonly contatados: number;
  readonly ganhos: number;
  /** Abaixo da amostra mínima a taxa não é apresentada (D29). */
  readonly amostraSuficiente: boolean;
}

export interface Conversao {
  readonly amostraMinima: number;
  readonly porOrigem: readonly GrupoConversao[];
  readonly porSegmento: readonly GrupoConversao[];
  /** Todos os leads abertos no período, somando as origens. */
  readonly total: Omit<GrupoConversao, 'chave' | 'nome'>;
}

export function toConversao(response: DashboardConversion): Conversao {
  const { minimumSample, bySource, bySegment } = response.conversion;
  const grupo = (row: (typeof bySource)[number], nome: string): GrupoConversao => ({
    ...(row.key === undefined ? {} : { chave: row.key }),
    nome,
    leads: row.leads,
    contatados: row.contacted,
    ganhos: row.won,
    amostraSuficiente: row.sufficientSample,
  });
  const leads = bySource.reduce((soma, row) => soma + row.leads, 0);
  return {
    amostraMinima: minimumSample,
    porOrigem: bySource.map((row) => grupo(row, nomeDaOrigem(row.key))),
    porSegmento: bySegment.map((row) => grupo(row, row.key ?? 'Sem segmento')),
    total: {
      leads,
      contatados: bySource.reduce((soma, row) => soma + row.contacted, 0),
      ganhos: bySource.reduce((soma, row) => soma + row.won, 0),
      amostraSuficiente: leads >= minimumSample,
    },
  };
}

function nomeDaOrigem(key: string | undefined): string {
  const tipo = SOURCE_TYPES.find((type) => type === key);
  return tipo === undefined ? 'Sem origem registrada' : SOURCE_TYPE_LABEL[tipo];
}

/**
 * Variação em relação ao período anterior de mesmo tamanho. Sem base (zero
 * antes), não há percentual: "+∞%" seria enganoso.
 */
export type Variacao =
  | { readonly tipo: 'percentual'; readonly valor: number }
  | { readonly tipo: 'semBase' }
  | { readonly tipo: 'semDados' };

export function variacao(atual: number, anterior: number | undefined): Variacao {
  if (anterior === undefined || (anterior === 0 && atual === 0)) {
    return { tipo: 'semDados' };
  }
  if (anterior === 0) {
    return { tipo: 'semBase' };
  }
  return { tipo: 'percentual', valor: ((atual - anterior) / anterior) * 100 };
}

/** Período imediatamente anterior, com o mesmo número de dias. */
export function periodoAnterior(periodo: PeriodoSelecionado): PeriodoSelecionado {
  const inicio = Date.parse(`${periodo.de}T00:00:00Z`);
  const fim = Date.parse(`${periodo.ate}T00:00:00Z`);
  const dia = 86_400_000;
  const novoFim = inicio - dia;
  return { de: isoDate(novoFim - (fim - inicio)), ate: isoDate(novoFim) };
}

function isoDate(epoch: number): string {
  return new Date(epoch).toISOString().slice(0, 10);
}
