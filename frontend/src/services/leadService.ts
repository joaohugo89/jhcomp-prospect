import type { Lead, LeadPriority, LeadStatus, TimelineEntry } from '../types/lead';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Leads (doc 05 §5). Moving a card is always a status change (D2). */

export function getLead(id: string, signal?: AbortSignal): Promise<Lead> {
  return request<Lead>(`/leads/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

export function createLead(companyId: string, priority: LeadPriority): Promise<Lead> {
  return request<Lead>('/leads', { method: 'POST', body: { companyId, priority } });
}

/**
 * Changes the status. `contactConfirmed` is only true when the operator has
 * confirmed, on screen, that the contact actually happened (doc 06 §2).
 */
export function changeLeadStatus(
  id: string,
  status: LeadStatus,
  reason: string,
  contactConfirmed: boolean,
): Promise<Lead> {
  return request<Lead>(`/leads/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: { status, reason: reason.trim() === '' ? undefined : reason.trim(), contactConfirmed },
  });
}

/** Sets the owner; `null` clears it. The server requires an active member of the organisation. */
export function assignLead(id: string, assignedUserId: string | null): Promise<Lead> {
  return request<Lead>(`/leads/${encodeURIComponent(id)}`, { method: 'PATCH', body: { assignedUserId } });
}

export function changeLeadPriority(id: string, priority: LeadPriority): Promise<Lead> {
  return request<Lead>(`/leads/${encodeURIComponent(id)}/priority`, { method: 'PATCH', body: { priority } });
}

export function getLeadTimeline(id: string, page: number, signal?: AbortSignal): Promise<Page<TimelineEntry>> {
  return request<Page<TimelineEntry>>(
    `/leads/${encodeURIComponent(id)}/timeline${toQuery({ page })}`,
    signal ? { signal } : {},
  );
}
