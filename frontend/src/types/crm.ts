import type { LeadPriority, LeadStatus } from './lead';

/** CRM and dashboard (doc 05 §3 and §9, D2, D25). */

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
  /** The deal's estimated value, in Brazilian reais. */
  readonly estimatedValue?: number;
  readonly lastInteractionType?: InteractionType;
  readonly lastInteractionAt?: string;
  readonly nextTaskTitle?: string;
  readonly nextTaskDueAt?: string;
  /** When the lead reached its current stage. */
  readonly stageEnteredAt: string;
  readonly allowedTransitions: readonly LeadStatus[];
}

export interface BoardColumn {
  readonly stage: Stage;
  readonly total: number;
  /** The sum over the whole stage, not only the cards shown, in reais. */
  readonly totalValue: number;
  /** In the final columns: only those closed within this many recent days. */
  readonly recentDays?: number;
  readonly cards: readonly BoardCard[];
}

/** `outcome` is derived from the lead's status (D2). */
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
    /** Active companies with no segment: a data quality signal. */
    readonly companiesWithoutSegment: number;
  };
}

export interface AttentionItem {
  readonly leadId: string;
  readonly companyName: string;
  readonly status: LeadStatus;
  /** Task due date, stage entry, last touch or last update, depending on the section. */
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
  /** Leads in the cohort that reached this stage or went beyond it. */
  readonly reached: number;
  /** How many have left the stage: the base for the average in days. */
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

/** One group's conversion (RF-071); an absent `key` means the segment was not set. */
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

/** One interval of the series; `start` is its first day inside the period. */
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
