import type { LeadPriority } from '../types/lead';
import type { Page } from '../types/page';
import type { SavedView, Segments, WorklistFilters, WorklistItem } from '../types/worklist';
import { request, toQuery } from './httpClient';

/** The lead work queue (D31): filters, one-click slices and sorting on the server. */
export function listWorklist(
  filters: WorklistFilters,
  page: number,
  signal?: AbortSignal,
): Promise<Page<WorklistItem>> {
  const query = toQuery({
    search: filters.search.trim(),
    status: filters.status,
    priority: filters.priority,
    city: filters.city.trim(),
    segment: filters.noSegment ? '' : filters.segment.trim(),
    noSegment: filters.noSegment,
    source: filters.source,
    assignee: filters.unassigned ? '' : filters.assignee,
    unassigned: filters.unassigned,
    scoreMin: filters.scoreMin.trim(),
    scoreMax: filters.scoreMax.trim(),
    focus: filters.focus,
    sort: filters.sort,
    direction: filters.direction,
    page,
  });
  return request<Page<WorklistItem>>(`/leads/worklist${query}`, signal ? { signal } : {});
}

export function getLeadSummary(id: string, signal?: AbortSignal): Promise<WorklistItem> {
  return request<WorklistItem>(`/leads/${encodeURIComponent(id)}/summary`, signal ? { signal } : {});
}

/** Segments in use, with company counts; for the filter and for suggestions in the form. */
export function getSegments(signal?: AbortSignal): Promise<Segments> {
  return request<Segments>('/companies/segments', signal ? { signal } : {});
}

/** The same change over several leads, all or nothing. Owner and priority only. */
export function bulkUpdateLeads(
  leadIds: readonly string[],
  change:
    | { readonly action: 'ASSIGN'; readonly assignedUserId: string | null }
    | { readonly action: 'PRIORITY'; readonly priority: LeadPriority },
): Promise<{ readonly updated: number }> {
  return request<{ readonly updated: number }>('/leads/bulk', { method: 'POST', body: { leadIds, ...change } });
}

export function listViews(signal?: AbortSignal): Promise<readonly SavedView[]> {
  return request<readonly SavedView[]>('/leads/views', signal ? { signal } : {});
}

/** `shared` publishes to the team; the server only accepts it from an administrator. */
export function createView(name: string, query: string, shared: boolean): Promise<SavedView> {
  return request<SavedView>('/leads/views', { method: 'POST', body: { name, query, shared } });
}

export function deleteView(id: string): Promise<void> {
  return request<void>(`/leads/views/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
