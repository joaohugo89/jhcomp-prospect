import type { Page } from '../types/page';
import type {
  ComposeContext,
  Message,
  NewSuppression,
  OutreachChannel,
  RecordSendInput,
  SuppressionEntry,
} from '../types/outreach';
import { request, toQuery } from './httpClient';

/**
 * Outreach (doc 05 §10). Nada aqui envia mensagem: `recordSend` registra um
 * envio que o operador já fez fora do sistema (regra 5).
 */

/** A geração chama o provedor de IA na mesma requisição; o prazo é maior que o padrão. */
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

/** Só administradores; operador recebe 403. */
export function listSuppressions(page: number, signal?: AbortSignal): Promise<Page<SuppressionEntry>> {
  return request<Page<SuppressionEntry>>(`/suppression-entries${toQuery({ page })}`, signal ? { signal } : {});
}
