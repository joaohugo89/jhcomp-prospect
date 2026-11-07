import type { Page } from '../types/page';
import type {
  ComposeContext,
  Message,
  IdentifierType,
  NewSuppression,
  OutreachChannel,
  RecordSendInput,
  SuppressionCheck,
  SuppressionEntry,
} from '../types/outreach';
import { request, toQuery } from './httpClient';

/**
 * Outreach (doc 05 §10). Nothing here sends a message: `recordSend` records a
 * send the operator has already made outside the system (rule 5).
 */

/** Generation calls the AI provider in the same request, so the deadline is longer than the default. */
const GENERATION_TIMEOUT_MS = 30_000;

function leadPath(leadId: string): string {
  return `/leads/${encodeURIComponent(leadId)}`;
}

function messagePath(messageId: string): string {
  return `/messages/${encodeURIComponent(messageId)}`;
}

export function getComposeContext(leadId: string, signal?: AbortSignal): Promise<ComposeContext> {
  return request<ComposeContext>(`${leadPath(leadId)}/outreach-context`, signal ? { signal } : {});
}

export function listMessages(leadId: string, signal?: AbortSignal): Promise<Page<Message>> {
  return request<Page<Message>>(`${leadPath(leadId)}/messages${toQuery({ size: 20 })}`, signal ? { signal } : {});
}

export function generateMessage(leadId: string, channel: OutreachChannel): Promise<Message> {
  return request<Message>(`${leadPath(leadId)}/messages/generate`, {
    method: 'POST',
    body: { channel },
    timeoutMs: GENERATION_TIMEOUT_MS,
  });
}

export function editMessage(messageId: string, subject: string | undefined, body: string): Promise<Message> {
  return request<Message>(messagePath(messageId), { method: 'PATCH', body: { subject, body } });
}

export function approveMessage(messageId: string, unverifiedClaimsReviewed: boolean): Promise<Message> {
  return request<Message>(`${messagePath(messageId)}/approve`, {
    method: 'POST',
    body: { unverifiedClaimsReviewed },
  });
}

export function recordSend(messageId: string, input: RecordSendInput): Promise<Message> {
  return request<Message>(`${messagePath(messageId)}/record-send`, {
    method: 'POST',
    body: { ...input, notes: input.notes.trim() === '' ? undefined : input.notes.trim() },
  });
}

export function cancelMessage(messageId: string): Promise<Message> {
  return request<Message>(`${messagePath(messageId)}/cancel`, { method: 'POST' });
}

export function registerSuppression(input: NewSuppression): Promise<SuppressionEntry> {
  return request<SuppressionEntry>('/suppression-entries', {
    method: 'POST',
    body: { ...input, identifier: input.identifier.trim(), reason: input.reason.trim() },
  });
}

/** Administrators only; an operator gets a 403. */
/** The administrator's list; `search` takes a contact in any format, normalised on the server. */
export function listSuppressions(
  filters: { readonly identifierType: IdentifierType | ''; readonly search: string },
  page: number,
  signal?: AbortSignal,
): Promise<Page<SuppressionEntry>> {
  return request<Page<SuppressionEntry>>(
    `/suppression-entries${toQuery({ identifierType: filters.identifierType, search: filters.search.trim(), page })}`,
    signal ? { signal } : {},
  );
}

export function getSuppressionSummary(signal?: AbortSignal): Promise<Readonly<Record<IdentifierType, number>>> {
  return request<Readonly<Record<IdentifierType, number>>>('/suppression-entries/summary', signal ? { signal } : {});
}

/** Any member can check a contact. A POST, so the contact never reaches the URL. */
export function checkSuppression(identifierType: IdentifierType, identifier: string): Promise<SuppressionCheck> {
  return request<SuppressionCheck>('/suppression-entries/check', {
    method: 'POST',
    body: { identifierType, identifier },
  });
}
