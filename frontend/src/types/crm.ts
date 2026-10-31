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
  readonly lastInteractionType?: InteractionType;
  readonly lastInteractionAt?: string;
  readonly nextTaskTitle?: string;
  readonly nextTaskDueAt?: string;
  readonly allowedTransitions: readonly LeadStatus[];
}

export interface BoardColumn {
  readonly stage: Stage;
  readonly total: number;
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
  };
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
