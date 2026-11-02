import type { LeadPriority } from '../types/lead';
import type { Page } from '../types/page';
import type { SavedView, Segments, WorklistFilters, WorklistItem } from '../types/worklist';
import { request, toQuery } from './httpClient';

/** Fila de trabalho de leads (D31): filtros, recortes e ordenação no servidor. */
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

/** Segmentos em uso, com quantas empresas; para o filtro e para sugerir no cadastro. */
export function getSegments(signal?: AbortSignal): Promise<Segments> {
  return request<Segments>('/companies/segments', signal ? { signal } : {});
}

/** Mesma mudança em vários leads, tudo ou nada. Só responsável e prioridade. */
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

export function createView(name: string, query: string): Promise<SavedView> {
  return request<SavedView>('/leads/views', { method: 'POST', body: { name, query } });
}

export function deleteView(id: string): Promise<void> {
  return request<void>(`/leads/views/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
