import type { Activity, BoardColumn, DashboardSummary, Deal, Interaction, NewInteraction, Task } from '../types/crm';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** CRM e dashboard (doc 05 §3 e §9). Mover card é `changeLeadStatus` (D2). */

export interface BoardFilters {
  readonly search: string;
  readonly segment: string;
  readonly city: string;
}

export function getBoard(filters: BoardFilters, signal?: AbortSignal): Promise<readonly BoardColumn[]> {
  const query = toQuery({
    search: filters.search.trim(),
    segment: filters.segment.trim(),
    city: filters.city.trim(),
  });
  return request<readonly BoardColumn[]>(`/pipeline/board${query}`, signal ? { signal } : {});
}

export function getDeal(leadId: string, signal?: AbortSignal): Promise<Deal> {
  return request<Deal>(`/leads/${encodeURIComponent(leadId)}/deal`, signal ? { signal } : {});
}

export function updateDeal(
  leadId: string,
  input: {
    readonly service: string;
    readonly estimatedValue: number | undefined;
    readonly lossReason: string;
  },
): Promise<Deal> {
  return request<Deal>(`/leads/${encodeURIComponent(leadId)}/deal`, {
    method: 'PUT',
    body: input,
  });
}

export function recordInteraction(leadId: string, input: NewInteraction): Promise<Interaction> {
  return request<Interaction>(`/leads/${encodeURIComponent(leadId)}/interactions`, { method: 'POST', body: input });
}

export function listTasks(leadId: string, signal?: AbortSignal): Promise<Page<Task>> {
  return request<Page<Task>>(
    `/leads/${encodeURIComponent(leadId)}/tasks${toQuery({ size: 50 })}`,
    signal ? { signal } : {},
  );
}

export function createTask(leadId: string, title: string, dueAt: string | undefined): Promise<Task> {
  return request<Task>('/tasks', {
    method: 'POST',
    body: { leadId, title, dueAt },
  });
}

/** O PATCH recebe o estado completo da tarefa. */
export function updateTask(task: Task, status: Task['status']): Promise<Task> {
  return request<Task>(`/tasks/${encodeURIComponent(task.id)}`, {
    method: 'PATCH',
    body: {
      title: task.title,
      dueAt: task.dueAt,
      assignedUserId: task.assignedUserId,
      status,
    },
  });
}

export interface DashboardFilters {
  readonly from: string;
  readonly to: string;
}

export function getDashboardSummary(filters: DashboardFilters, signal?: AbortSignal): Promise<DashboardSummary> {
  return request<DashboardSummary>(
    `/dashboard/summary${toQuery({ from: filters.from, to: filters.to })}`,
    signal ? { signal } : {},
  );
}

export function getDashboardActivity(
  filters: DashboardFilters,
  page: number,
  signal?: AbortSignal,
): Promise<Page<Activity>> {
  return request<Page<Activity>>(
    `/dashboard/activity${toQuery({ from: filters.from, to: filters.to, page, size: 15 })}`,
    signal ? { signal } : {},
  );
}
