import type {
  Atencao,
  AtividadeRecente,
  Conversao,
  DashboardData,
  Funil,
  MetricasPeriodo,
  SerieTemporal,
} from './dashboardData';

/**
 * Dados simulados do dashboard, para testes e para a pré-visualização em
 * desenvolvimento. Empresas e pessoas são fictícias (doc 10 §10).
 */
export const mockDashboardData: DashboardData = {
  periodo: { de: '2026-10-08', ate: '2026-11-06' },
  metricasPeriodo: {
    empresasAdicionadas: 24,
    contatosRegistrados: 9,
    negociosGanhos: 2,
    valorGanho: 4800,
  },
  metricasAtuais: {
    leadsAtivos: 31,
    propostasEmAberto: 3,
    tarefasAbertas: 7,
    tarefasAtrasadas: 2,
  },
  leadsPorStatus: [
    { status: 'NEW', nome: 'Novo', quantidade: 12 },
    { status: 'QUALIFIED', nome: 'Qualificado', quantidade: 8 },
    { status: 'CONTACT_APPROVED', nome: 'Contato aprovado', quantidade: 4 },
    { status: 'CONTACTED', nome: 'Contatado', quantidade: 3 },
    { status: 'IN_CONVERSATION', nome: 'Em conversa', quantidade: 1 },
    { status: 'PROPOSAL_SENT', nome: 'Proposta enviada', quantidade: 3 },
    { status: 'WON', nome: 'Ganho', quantidade: 2 },
    { status: 'LOST', nome: 'Perdido', quantidade: 0 },
    { status: 'DO_NOT_CONTACT', nome: 'Não contatar', quantidade: 0 },
  ],
  empresasSemSegmento: 6,
};

const LEAD_A = '00000000-0000-4000-8000-00000000000a';
const LEAD_B = '00000000-0000-4000-8000-00000000000b';
const LEAD_C = '00000000-0000-4000-8000-00000000000c';

export const mockAtividadesRecentes: readonly AtividadeRecente[] = [
  {
    id: 'mock-1',
    leadId: LEAD_A,
    empresa: 'Barbearia Exemplo',
    transicao: { tipo: 'status', de: 'PROPOSAL_SENT', para: 'WON' },
    data: '2026-11-06T13:10:00Z',
    usuario: 'Operador Fictício',
  },
  {
    id: 'mock-2',
    leadId: LEAD_B,
    empresa: 'Padaria Modelo',
    transicao: { tipo: 'interacao', interacao: 'CONTACT', canal: 'WHATSAPP' },
    data: '2026-11-06T11:45:00Z',
    usuario: 'Operador Fictício',
  },
  {
    id: 'mock-3',
    leadId: LEAD_C,
    empresa: 'Oficina Demonstração',
    transicao: { tipo: 'status', para: 'NEW' },
    data: '2026-11-05T18:20:00Z',
    usuario: 'Administrador Fictício',
  },
];

/** Período anterior de mesmo tamanho, para a variação do destaque. */
export const mockMetricasAnteriores: MetricasPeriodo = {
  empresasAdicionadas: 18,
  contatosRegistrados: 9,
  negociosGanhos: 1,
  valorGanho: 3200,
};

/** Cinco semanas do período, somando os totais de `mockDashboardData`. */
export const mockSerie: SerieTemporal = {
  granularidade: 'WEEK',
  pontos: [
    { inicio: '2026-10-08', empresasAdicionadas: 3, contatosRegistrados: 1, negociosGanhos: 0, valorGanho: 0 },
    { inicio: '2026-10-12', empresasAdicionadas: 5, contatosRegistrados: 2, negociosGanhos: 0, valorGanho: 0 },
    { inicio: '2026-10-19', empresasAdicionadas: 4, contatosRegistrados: 1, negociosGanhos: 1, valorGanho: 1800 },
    { inicio: '2026-10-26', empresasAdicionadas: 7, contatosRegistrados: 3, negociosGanhos: 0, valorGanho: 0 },
    { inicio: '2026-11-02', empresasAdicionadas: 5, contatosRegistrados: 2, negociosGanhos: 1, valorGanho: 3000 },
  ],
};

export const mockConversao: Conversao = {
  amostraMinima: 10,
  porOrigem: [
    { chave: 'CSV_IMPORT', nome: 'Importação CSV', leads: 18, contatados: 6, ganhos: 2, amostraSuficiente: true },
    { chave: 'MANUAL', nome: 'Manual', leads: 6, contatados: 3, ganhos: 0, amostraSuficiente: false },
  ],
  porSegmento: [
    { chave: 'Barbearia', nome: 'Barbearia', leads: 11, contatados: 5, ganhos: 1, amostraSuficiente: true },
    { nome: 'Sem segmento', leads: 13, contatados: 4, ganhos: 1, amostraSuficiente: true },
  ],
  total: { leads: 24, contatados: 9, ganhos: 2, amostraSuficiente: true },
};

/** Referência das datas de `mockAtencao`. */
export const MOCK_NOW = Date.parse('2026-11-06T15:00:00Z');

export const mockAtencao: Atencao = {
  tarefas: {
    total: 2,
    itens: [
      {
        leadId: LEAD_B,
        empresa: 'Padaria Modelo',
        status: 'CONTACTED',
        em: '2026-11-04T13:00:00Z',
        detalhe: 'Enviar orçamento',
        atrasada: true,
      },
      {
        leadId: LEAD_C,
        empresa: 'Oficina Demonstração',
        status: 'QUALIFIED',
        em: '2026-11-06T20:00:00Z',
        detalhe: 'Ligar para o responsável',
        atrasada: false,
      },
    ],
  },
  parados: {
    total: 1,
    itens: [
      {
        leadId: LEAD_C,
        empresa: 'Oficina Demonstração',
        status: 'QUALIFIED',
        em: '2026-10-27T12:00:00Z',
        atrasada: false,
      },
    ],
  },
  propostasFrias: { total: 0, itens: [] },
  semTarefa: {
    total: 1,
    itens: [
      {
        leadId: LEAD_A,
        empresa: 'Barbearia Exemplo',
        status: 'IN_CONVERSATION',
        em: '2026-11-03T12:00:00Z',
        atrasada: false,
      },
    ],
  },
};

export const mockFunil: Funil = {
  amostraMinima: 10,
  coorte: 24,
  etapas: [
    { status: 'NEW', nome: 'Novo', alcancados: 24, mediaDias: 1.5, saidas: 18 },
    { status: 'QUALIFIED', nome: 'Qualificado', alcancados: 18, passagem: 0.75, mediaDias: 2.4, saidas: 12 },
    {
      status: 'CONTACT_APPROVED',
      nome: 'Contato aprovado',
      alcancados: 12,
      passagem: 0.667,
      mediaDias: 1.1,
      saidas: 10,
    },
    { status: 'CONTACTED', nome: 'Contatado', alcancados: 9, passagem: 0.75, saidas: 7 },
    { status: 'IN_CONVERSATION', nome: 'Em conversa', alcancados: 6, saidas: 5 },
    { status: 'PROPOSAL_SENT', nome: 'Proposta enviada', alcancados: 5, saidas: 3 },
    { status: 'WON', nome: 'Ganho', alcancados: 2, saidas: 0 },
  ],
};
