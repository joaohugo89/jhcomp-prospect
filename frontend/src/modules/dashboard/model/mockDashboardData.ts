import type {
  AttentionView,
  RecentActivity,
  ConversionView,
  DashboardData,
  FunnelView,
  PeriodMetrics,
  TimeSeries,
} from './dashboardData';

/**
 * Mock dashboard data, for tests and for the development preview. Companies
 * and people are fictitious (doc 10 §10).
 */
export const mockDashboardData: DashboardData = {
  period: { from: '2026-10-08', to: '2026-11-06' },
  periodMetrics: {
    companiesAdded: 24,
    contactsRegistered: 9,
    dealsWon: 2,
    wonValue: 4800,
  },
  currentMetrics: {
    activeLeads: 31,
    openProposals: 3,
    openTasks: 7,
    overdueTasks: 2,
  },
  leadsByStatus: [
    { status: 'NEW', label: 'Novo', count: 12 },
    { status: 'QUALIFIED', label: 'Qualificado', count: 8 },
    { status: 'CONTACT_APPROVED', label: 'Contato aprovado', count: 4 },
    { status: 'CONTACTED', label: 'Contatado', count: 3 },
    { status: 'IN_CONVERSATION', label: 'Em conversa', count: 1 },
    { status: 'PROPOSAL_SENT', label: 'Proposta enviada', count: 3 },
    { status: 'WON', label: 'Ganho', count: 2 },
    { status: 'LOST', label: 'Perdido', count: 0 },
    { status: 'DO_NOT_CONTACT', label: 'Não contatar', count: 0 },
  ],
  companiesWithoutSegment: 6,
};

const LEAD_A = '00000000-0000-4000-8000-00000000000a';
const LEAD_B = '00000000-0000-4000-8000-00000000000b';
const LEAD_C = '00000000-0000-4000-8000-00000000000c';

export const mockRecentActivities: readonly RecentActivity[] = [
  {
    id: 'mock-1',
    leadId: LEAD_A,
    company: 'Barbearia Exemplo',
    transition: { kind: 'status', from: 'PROPOSAL_SENT', to: 'WON' },
    at: '2026-11-06T13:10:00Z',
    user: 'Operador Fictício',
  },
  {
    id: 'mock-2',
    leadId: LEAD_B,
    company: 'Padaria Modelo',
    transition: { kind: 'interaction', interaction: 'CONTACT', channel: 'WHATSAPP' },
    at: '2026-11-06T11:45:00Z',
    user: 'Operador Fictício',
  },
  {
    id: 'mock-3',
    leadId: LEAD_C,
    company: 'Oficina Demonstração',
    transition: { kind: 'status', to: 'NEW' },
    at: '2026-11-05T18:20:00Z',
    user: 'Administrador Fictício',
  },
];

/** The previous period of the same length, for the hero's change indicator. */
export const mockPreviousMetrics: PeriodMetrics = {
  companiesAdded: 18,
  contactsRegistered: 9,
  dealsWon: 1,
  wonValue: 3200,
};

/** Five weeks of the period, summing to the totals in `mockDashboardData`. */
export const mockTimeSeries: TimeSeries = {
  granularity: 'WEEK',
  points: [
    { start: '2026-10-08', companiesAdded: 3, contactsRegistered: 1, dealsWon: 0, wonValue: 0 },
    { start: '2026-10-12', companiesAdded: 5, contactsRegistered: 2, dealsWon: 0, wonValue: 0 },
    { start: '2026-10-19', companiesAdded: 4, contactsRegistered: 1, dealsWon: 1, wonValue: 1800 },
    { start: '2026-10-26', companiesAdded: 7, contactsRegistered: 3, dealsWon: 0, wonValue: 0 },
    { start: '2026-11-02', companiesAdded: 5, contactsRegistered: 2, dealsWon: 1, wonValue: 3000 },
  ],
};

export const mockConversion: ConversionView = {
  minimumSample: 10,
  bySource: [
    { key: 'CSV_IMPORT', label: 'Importação CSV', leads: 18, contacted: 6, won: 2, sufficientSample: true },
    { key: 'MANUAL', label: 'Manual', leads: 6, contacted: 3, won: 0, sufficientSample: false },
  ],
  bySegment: [
    { key: 'Barbearia', label: 'Barbearia', leads: 11, contacted: 5, won: 1, sufficientSample: true },
    { label: 'Sem segmento', leads: 13, contacted: 4, won: 1, sufficientSample: true },
  ],
  total: { leads: 24, contacted: 9, won: 2, sufficientSample: true },
};

/** The reference date for `mockAttention`. */
export const MOCK_NOW = Date.parse('2026-11-06T15:00:00Z');

export const mockAttention: AttentionView = {
  tasks: {
    total: 2,
    items: [
      {
        leadId: LEAD_B,
        company: 'Padaria Modelo',
        status: 'CONTACTED',
        at: '2026-11-04T13:00:00Z',
        detail: 'Enviar orçamento',
        overdue: true,
      },
      {
        leadId: LEAD_C,
        company: 'Oficina Demonstração',
        status: 'QUALIFIED',
        at: '2026-11-06T20:00:00Z',
        detail: 'Ligar para o responsável',
        overdue: false,
      },
    ],
  },
  staleLeads: {
    total: 1,
    items: [
      {
        leadId: LEAD_C,
        company: 'Oficina Demonstração',
        status: 'QUALIFIED',
        at: '2026-10-27T12:00:00Z',
        overdue: false,
      },
    ],
  },
  quietProposals: { total: 0, items: [] },
  withoutTask: {
    total: 1,
    items: [
      {
        leadId: LEAD_A,
        company: 'Barbearia Exemplo',
        status: 'IN_CONVERSATION',
        at: '2026-11-03T12:00:00Z',
        overdue: false,
      },
    ],
  },
};

export const mockFunnel: FunnelView = {
  minimumSample: 10,
  cohort: 24,
  stages: [
    { status: 'NEW', label: 'Novo', reached: 24, avgDays: 1.5, exits: 18 },
    { status: 'QUALIFIED', label: 'Qualificado', reached: 18, passthrough: 0.75, avgDays: 2.4, exits: 12 },
    {
      status: 'CONTACT_APPROVED',
      label: 'Contato aprovado',
      reached: 12,
      passthrough: 0.667,
      avgDays: 1.1,
      exits: 10,
    },
    { status: 'CONTACTED', label: 'Contatado', reached: 9, passthrough: 0.75, exits: 7 },
    { status: 'IN_CONVERSATION', label: 'Em conversa', reached: 6, exits: 5 },
    { status: 'PROPOSAL_SENT', label: 'Proposta enviada', reached: 5, exits: 3 },
    { status: 'WON', label: 'Ganho', reached: 2, exits: 0 },
  ],
};
