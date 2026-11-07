import type { SourceType } from './company';
import type { LeadPriority, LeadStatus } from './lead';

/** A work queue row (`/leads/worklist`) and one lead's summary (`/leads/{id}/summary`). */
export interface WorklistItem {
  readonly leadId: string;
  readonly companyId: string;
  readonly companyName: string;
  readonly segment?: string;
  readonly city: string;
  readonly state: string;
  readonly status: LeadStatus;
  readonly priority: LeadPriority;
  readonly score?: number;
  readonly assignedUserId?: string;
  readonly assigneeName?: string;
  /** The deal's value, in Brazilian reais. */
  readonly estimatedValue?: number;
  readonly nextTaskTitle?: string;
  readonly nextTaskDueAt?: string;
  /** When it reached the current stage. */
  readonly stageEnteredAt?: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export const WORKLIST_FOCUS = ['OVERDUE', 'STALE', 'WITHOUT_TASK'] as const;
export type WorklistFocus = (typeof WORKLIST_FOCUS)[number];

export const WORKLIST_SORT = ['COMPANY', 'SCORE', 'CREATED', 'UPDATED', 'STAGE', 'VALUE', 'NEXT_TASK'] as const;
export type WorklistSort = (typeof WORKLIST_SORT)[number];

/** The queue's filters; an empty string filters nothing. All of them go into the URL. */
export interface WorklistFilters {
  readonly search: string;
  readonly status: LeadStatus | '';
  readonly priority: LeadPriority | '';
  readonly city: string;
  readonly segment: string;
  /** Only companies with no segment. */
  readonly noSegment: boolean;
  readonly source: SourceType | '';
  readonly assignee: string;
  readonly unassigned: boolean;
  readonly scoreMin: string;
  readonly scoreMax: string;
  readonly focus: WorklistFocus | '';
  readonly sort: WorklistSort;
  readonly direction: 'asc' | 'desc';
}

export interface Segment {
  readonly name: string;
  readonly companies: number;
}

export interface Segments {
  readonly segments: readonly Segment[];
  readonly withoutSegment: number;
}

export interface SavedView {
  readonly id: string;
  readonly name: string;
  /** The list's query string, already encoded. */
  readonly query: string;
  /** Published to the team by an administrator. */
  readonly shared: boolean;
  /** The server would accept a delete: the owner of a private view, or an admin for a shared one. */
  readonly canDelete: boolean;
  readonly createdAt: string;
}
