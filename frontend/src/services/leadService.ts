import type { Lead, LeadFilters, LeadPriority, LeadStatus, TimelineEntry } from '../types/lead';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Leads (doc 05 §5). Mover card é sempre mudança de status (D2). */

export function listLeads(filters: LeadFilters, page: number, signal?: AbortSignal): Promise<Page<Lead>> {
  const query = toQuery({
    search: filters.search.trim(),
    status: filters.status,
    priority: filters.priority,
    city: filters.city.trim(),
    page,
  });
  return request<Page<Lead>>(`/leads${query}`, signal ? { signal } : {});
}

export function getLead(id: string, signal?: AbortSignal): Promise<Lead> {
  return request<Lead>(`/leads/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

export function createLead(companyId: string, priority: LeadPriority): Promise<Lead> {
  return request<Lead>('/leads', { method: 'POST', body: { companyId, priority } });
}

/**
 * Muda o status. `contactConfirmed` só é verdadeiro quando o operador
 * confirmou, na tela, que o contato aconteceu (doc 06 §2).
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

export function changeLeadPriority(id: string, priority: LeadPriority): Promise<Lead> {
  return request<Lead>(`/leads/${encodeURIComponent(id)}/priority`, { method: 'PATCH', body: { priority } });
}

export function getLeadTimeline(id: string, page: number, signal?: AbortSignal): Promise<Page<TimelineEntry>> {
  return request<Page<TimelineEntry>>(
    `/leads/${encodeURIComponent(id)}/timeline${toQuery({ page })}`,
    signal ? { signal } : {},
  );
}
