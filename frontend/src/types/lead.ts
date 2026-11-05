import type { SourceType } from './company';
import type { InteractionChannel, InteractionDirection, InteractionType } from './crm';

/** Ciclo de vida do lead (doc 06 §2). */
export const LEAD_STATUSES = [
  'NEW',
  'QUALIFIED',
  'CONTACT_APPROVED',
  'CONTACTED',
  'IN_CONVERSATION',
  'PROPOSAL_SENT',
  'WON',
  'LOST',
  'DO_NOT_CONTACT',
] as const;

export type LeadStatus = (typeof LEAD_STATUSES)[number];

export const LEAD_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'] as const;

export type LeadPriority = (typeof LEAD_PRIORITIES)[number];

export interface LeadCompany {
  readonly id: string;
  readonly name: string;
  readonly segment?: string;
  readonly city: string;
  readonly state: string;
  readonly archived: boolean;
}

/** Espelha LeadResponses do backend (doc 05 §5). `score` ausente = sem análise (D7). */
export interface Lead {
  readonly id: string;
  readonly company: LeadCompany;
  readonly status: LeadStatus;
  readonly priority: LeadPriority;
  readonly score?: number;
  readonly scoreVersion?: string;
  readonly assignedUserId?: string;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly closedAt?: string;
  readonly allowedTransitions: readonly LeadStatus[];
}

export interface TimelineEntry {
  readonly type: 'STATUS_CHANGE' | 'SOURCE' | 'INTERACTION';
  readonly id: string;
  readonly occurredAt: string;
  readonly actorName?: string;
  readonly fromStatus?: LeadStatus;
  readonly toStatus?: LeadStatus;
  readonly reason?: string;
  readonly sourceType?: SourceType;
  readonly sourceName?: string;
  readonly sourceUrl?: string;
  readonly sourceReference?: string;
  readonly interactionType?: InteractionType;
  readonly channel?: InteractionChannel;
  readonly direction?: InteractionDirection;
  readonly outcome?: string;
  readonly notes?: string;
}

