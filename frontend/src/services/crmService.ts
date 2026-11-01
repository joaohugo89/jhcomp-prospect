import type { SourceType } from '../types/company';
import type {
  Activity,
  BoardColumn,
  DashboardAttention,
  DashboardConversion,
  DashboardFunnel,
  DashboardSummary,
  DashboardTimeline,
  Granularity,
  Deal,
  Interaction,
  NewInteraction,
  Task,
} from '../types/crm';
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

/** Período em datas de calendário; texto vazio é "sem filtro". */
export interface DashboardFilters {
  readonly from: string;
  readonly to: string;
  readonly segment: string;
  readonly city: string;
  readonly source: SourceType | '';
  /** Id do responsável; vazio é todos. */
  readonly assignee: string;
}

function dashboardQuery(
  filters: DashboardFilters,
  extra: Readonly<Record<string, string | number | undefined>> = {},
): string {
  const { from, to, segment, city, source, assignee } = filters;
  return toQuery({ from, to, segment: segment.trim(), city: city.trim(), source, assignee, ...extra });
}

export function getDashboardSummary(filters: DashboardFilters, signal?: AbortSignal): Promise<DashboardSummary> {
  return request<DashboardSummary>(`/dashboard/summary${dashboardQuery(filters)}`, signal ? { signal } : {});
}

/** Sem `granularity`, o servidor escolhe pelo tamanho do período. */
export function getDashboardTimeline(
  filters: DashboardFilters,
  granularity: Granularity | undefined,
  signal?: AbortSignal,
): Promise<DashboardTimeline> {
  return request<DashboardTimeline>(
    `/dashboard/timeline${dashboardQuery(filters, { granularity })}`,
    signal ? { signal } : {},
  );
}

/** O que pede ação agora; os 5 mais urgentes de cada seção. */
export function getDashboardAttention(filters: DashboardFilters, signal?: AbortSignal): Promise<DashboardAttention> {
  return request<DashboardAttention>(
    `/dashboard/attention${dashboardQuery(filters, { limit: 5 })}`,
    signal ? { signal } : {},
  );
}

export function getDashboardFunnel(filters: DashboardFilters, signal?: AbortSignal): Promise<DashboardFunnel> {
  return request<DashboardFunnel>(`/dashboard/funnel${dashboardQuery(filters)}`, signal ? { signal } : {});
}

export function getDashboardConversion(filters: DashboardFilters, signal?: AbortSignal): Promise<DashboardConversion> {
  return request<DashboardConversion>(`/dashboard/conversion${dashboardQuery(filters)}`, signal ? { signal } : {});
}

export function getDashboardActivity(
  filters: DashboardFilters,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<Page<Activity>> {
  return request<Page<Activity>>(
    `/dashboard/activity${dashboardQuery(filters, { page, size })}`,
    signal ? { signal } : {},
  );
}
