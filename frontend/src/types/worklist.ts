import type { SourceType } from './company';
import type { LeadPriority, LeadStatus } from './lead';

/** Linha da fila de trabalho (`/leads/worklist`) e resumo de um lead (`/leads/{id}/summary`). */
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
  /** Valor do negócio, em reais. */
  readonly estimatedValue?: number;
  readonly nextTaskTitle?: string;
  readonly nextTaskDueAt?: string;
  /** Quando chegou à etapa atual. */
  readonly stageEnteredAt?: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export const WORKLIST_FOCUS = ['OVERDUE', 'STALE', 'WITHOUT_TASK'] as const;
export type WorklistFocus = (typeof WORKLIST_FOCUS)[number];

export const WORKLIST_SORT = ['COMPANY', 'SCORE', 'CREATED', 'UPDATED', 'STAGE', 'VALUE', 'NEXT_TASK'] as const;
export type WorklistSort = (typeof WORKLIST_SORT)[number];

/** Filtros da fila; texto vazio não filtra. Todos vão para a URL. */
export interface WorklistFilters {
  readonly search: string;
  readonly status: LeadStatus | '';
  readonly priority: LeadPriority | '';
  readonly city: string;
  readonly segment: string;
  /** Só empresas sem segmento. */
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
  /** Query string da lista, já codificada. */
  readonly query: string;
  /** Publicada para a equipe pelo administrador. */
  readonly shared: boolean;
  /** O servidor aceitaria apagar: dono da privada, ou administrador na compartilhada. */
  readonly canDelete: boolean;
  readonly createdAt: string;
}
