import type { LeadPriority, LeadStatus } from './lead';

/** CRM e dashboard (doc 05 §3 e §9, D2, D25). */

export interface Stage {
  readonly key: LeadStatus;
  readonly name: string;
  readonly position: number;
  readonly terminal: boolean;
}

export const INTERACTION_TYPES = ['CONTACT', 'REPLY', 'MEETING', 'PROPOSAL', 'DO_NOT_CONTACT_REQUEST', 'NOTE'] as const;
export type InteractionType = (typeof INTERACTION_TYPES)[number];

export const INTERACTION_CHANNELS = ['PHONE', 'WHATSAPP', 'EMAIL', 'INSTAGRAM', 'IN_PERSON', 'OTHER'] as const;
export type InteractionChannel = (typeof INTERACTION_CHANNELS)[number];

export const INTERACTION_DIRECTIONS = ['OUTBOUND', 'INBOUND', 'INTERNAL'] as const;
export type InteractionDirection = (typeof INTERACTION_DIRECTIONS)[number];

export interface BoardCard {
  readonly leadId: string;
  readonly companyName: string;
  readonly segment?: string;
  readonly city: string;
  readonly state: string;
  readonly status: LeadStatus;
  readonly priority: LeadPriority;
  readonly score?: number;
  readonly service?: string;
  /** Valor estimado do negócio, em reais. */
  readonly estimatedValue?: number;
  readonly lastInteractionType?: InteractionType;
  readonly lastInteractionAt?: string;
  readonly nextTaskTitle?: string;
  readonly nextTaskDueAt?: string;
  /** Quando o lead chegou na etapa atual. */
  readonly stageEnteredAt: string;
  readonly allowedTransitions: readonly LeadStatus[];
}

export interface BoardColumn {
  readonly stage: Stage;
  readonly total: number;
  /** Soma dos negócios da etapa inteira (não só dos cards mostrados), em reais. */
  readonly totalValue: number;
  /** Nas colunas finais: só os encerrados nesses últimos dias. */
  readonly recentDays?: number;
  readonly cards: readonly BoardCard[];
}

/** `outcome` vem do status do lead (D2). */
export interface Deal {
  readonly leadId: string;
  readonly service?: string;
  readonly estimatedValue?: number;
  readonly currency: string;
  readonly lossReason?: string;
  readonly outcome: 'OPEN' | 'WON' | 'LOST';
  readonly updatedAt: string;
}

export interface Interaction {
  readonly id: string;
  readonly type: InteractionType;
  readonly channel: InteractionChannel;
  readonly direction: InteractionDirection;
  readonly occurredAt: string;
  readonly outcome?: string;
  readonly notes?: string;
}

export interface NewInteraction {
  readonly type: InteractionType;
  readonly channel: InteractionChannel;
  readonly direction: InteractionDirection;
  readonly occurredAt: string;
  readonly outcome: string;
  readonly notes: string;
}

export interface Task {
  readonly id: string;
  readonly leadId: string;
  readonly title: string;
  readonly dueAt?: string;
  readonly status: 'OPEN' | 'DONE';
  readonly assignedUserId?: string;
  readonly createdAt: string;
  readonly completedAt?: string;
}

export interface DashboardSummary {
  readonly from: string;
  readonly to: string;
  readonly summary: {
    readonly leadsByStatus: Readonly<Partial<Record<LeadStatus, number>>>;
    readonly activeLeads: number;
    readonly companiesAdded: number;
    readonly contactsRegistered: number;
    readonly openProposals: number;
    readonly dealsWon: number;
    readonly wonValue: number;
    readonly openTasks: number;
    readonly overdueTasks: number;
    /** Empresas ativas sem segmento: qualidade dos dados. */
    readonly companiesWithoutSegment: number;
  };
}

export interface AttentionItem {
  readonly leadId: string;
  readonly companyName: string;
  readonly status: LeadStatus;
  /** Prazo da tarefa, entrada na etapa, último toque ou última atualização, conforme a seção. */
  readonly at: string;
  readonly detail?: string;
  readonly overdue: boolean;
}

export interface AttentionSection {
  readonly total: number;
  readonly items: readonly AttentionItem[];
}

export interface DashboardAttention {
  readonly tasks: AttentionSection;
  readonly staleLeads: AttentionSection;
  readonly quietProposals: AttentionSection;
  readonly withoutTask: AttentionSection;
}

export interface FunnelStage {
  readonly status: LeadStatus;
  /** Leads da coorte que chegaram a esta etapa ou além. */
  readonly reached: number;
  /** Quantos já saíram da etapa: base da média de dias. */
  readonly exits: number;
  readonly avgDays?: number;
  readonly sufficientSample: boolean;
}

export interface DashboardFunnel {
  readonly from: string;
  readonly to: string;
  readonly funnel: {
    readonly minimumSample: number;
    readonly cohort: number;
    readonly stages: readonly FunnelStage[];
  };
}

/** Conversão de um grupo (RF-071); `key` ausente é segmento não informado. */
export interface ConversionRow {
  readonly key?: string;
  readonly leads: number;
  readonly contacted: number;
  readonly won: number;
  readonly sufficientSample: boolean;
}

export interface DashboardConversion {
  readonly from: string;
  readonly to: string;
  readonly conversion: {
    readonly minimumSample: number;
    readonly bySource: readonly ConversionRow[];
    readonly bySegment: readonly ConversionRow[];
  };
}

export type Granularity = 'DAY' | 'WEEK' | 'MONTH';

/** Um intervalo da série; `start` é o primeiro dia dele dentro do período. */
export interface TimelinePoint {
  readonly start: string;
  readonly companiesAdded: number;
  readonly contactsRegistered: number;
  readonly dealsWon: number;
  readonly wonValue: number;
}

export interface DashboardTimeline {
  readonly from: string;
  readonly to: string;
  readonly granularity: Granularity;
  readonly points: readonly TimelinePoint[];
}

export interface Activity {
  readonly type: 'STATUS_CHANGE' | 'INTERACTION';
  readonly leadId: string;
  readonly companyName: string;
  readonly occurredAt: string;
  readonly actorName?: string;
  readonly fromStatus?: LeadStatus;
  readonly toStatus?: LeadStatus;
  readonly interactionType?: InteractionType;
  readonly channel?: InteractionChannel;
}
