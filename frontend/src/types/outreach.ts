import type { LeadStatus } from './lead';

/** Outreach e lista de não contato (doc 05 §10, D26). */

export const OUTREACH_CHANNELS = ['WHATSAPP', 'EMAIL', 'INSTAGRAM', 'PHONE_CALL'] as const;
export type OutreachChannel = (typeof OUTREACH_CHANNELS)[number];

export type MessageStatus = 'DRAFT' | 'NEEDS_REVIEW' | 'APPROVED' | 'SENT' | 'FAILED' | 'CANCELLED';

export const IDENTIFIER_TYPES = ['PHONE', 'EMAIL', 'INSTAGRAM'] as const;
export type IdentifierType = (typeof IDENTIFIER_TYPES)[number];

/** Limite de caracteres por canal; o servidor aplica o mesmo. */
export const CHANNEL_MAX_LENGTH: Readonly<Record<OutreachChannel, number>> = {
  WHATSAPP: 1000,
  EMAIL: 4000,
  INSTAGRAM: 1000,
  PHONE_CALL: 2000,
};

export interface Message {
  readonly id: string;
  readonly leadId: string;
  readonly channel: OutreachChannel;
  readonly subject?: string;
  readonly body: string;
  readonly unverifiedClaims: readonly string[];
  readonly status: MessageStatus;
  readonly generatedBy?: string;
  readonly promptVersion?: string;
  readonly approvedAt?: string;
  readonly sentAt?: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** `recipient` ausente: a empresa não tem contato válido nesse canal. */
export interface ChannelOption {
  readonly channel: OutreachChannel;
  readonly recipient?: string;
  readonly blocked: boolean;
}

export interface ComposeContext {
  readonly senderOrganization: string;
  readonly senderName: string;
  readonly leadStatus: LeadStatus;
  readonly companyName: string;
  readonly segment?: string;
  readonly city: string;
  readonly state: string;
  readonly confirmedFindings: readonly string[];
  readonly channels: readonly ChannelOption[];
}

export interface RecordSendInput {
  readonly result: 'SENT' | 'FAILED';
  readonly sentAt: string;
  readonly notes: string;
  readonly markLeadContacted: boolean;
}

export interface SuppressionEntry {
  readonly id: string;
  readonly identifierType: IdentifierType;
  readonly identifier: string;
  /** Ausente: vale para todos os canais. */
  readonly channel?: OutreachChannel;
  readonly reason: string;
  readonly requestedAt: string;
  readonly createdAt: string;
}

export interface NewSuppression {
  readonly identifierType: IdentifierType;
  readonly identifier: string;
  readonly channel: OutreachChannel | undefined;
  readonly reason: string;
}
