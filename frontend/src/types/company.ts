import type { LeadPriority, LeadStatus } from './lead';

/** Espelha CompanyResponses do backend (doc 05 §4). Campos opcionais vêm ausentes quando nulos. */
export interface Company {
  readonly id: string;
  readonly name: string;
  readonly tradeName?: string;
  readonly segment?: string;
  readonly city: string;
  readonly state: string;
  readonly websiteUrl?: string;
  readonly instagramUrl?: string;
  readonly phone?: string;
  readonly businessEmail?: string;
  readonly address?: string;
  readonly notes?: string;
  readonly archived: boolean;
  readonly archivedAt?: string;
  readonly createdAt: string;
  readonly updatedAt: string;
}

/** Campos editáveis. Texto vazio significa "sem valor". */
export interface CompanyInput {
  readonly name: string;
  readonly tradeName: string;
  readonly segment: string;
  readonly city: string;
  readonly state: string;
  readonly websiteUrl: string;
  readonly instagramUrl: string;
  readonly phone: string;
  readonly businessEmail: string;
  readonly address: string;
  readonly notes: string;
}

export interface SourceInput {
  readonly name: string;
  readonly reference: string;
  readonly url: string;
}

export const SOURCE_TYPES = ['MANUAL', 'CSV_IMPORT', 'INTEGRATION'] as const;
export type SourceType = (typeof SOURCE_TYPES)[number];

export interface Source {
  readonly id: string;
  readonly leadId?: string;
  readonly type: SourceType;
  readonly name: string;
  readonly reference?: string;
  readonly url?: string;
  readonly collectedAt: string;
  readonly collectedByUserId?: string;
  readonly note?: string;
}

export type DuplicateSignal = 'DOMAIN' | 'PHONE' | 'NAME_CITY_STATE';

export interface DuplicateCandidate {
  readonly companyId: string;
  readonly name: string;
  readonly city: string;
  readonly state: string;
  readonly archived: boolean;
  readonly signals: readonly DuplicateSignal[];
}

/** Recortes de um clique da lista de empresas. Sem ARCHIVED, só as ativas. */
export const COMPANY_FOCUS = ['WITHOUT_LEAD', 'WITHOUT_CONTACT', 'DUPLICATE', 'ARCHIVED'] as const;
export type CompanyFocus = (typeof COMPANY_FOCUS)[number];

export const COMPANY_SORT = ['NAME', 'CITY', 'CREATED', 'LAST_ACTIVITY'] as const;
export type CompanySort = (typeof COMPANY_SORT)[number];

/** Filtros da lista de empresas, na forma da URL. */
export interface CompanyWorklistFilters {
  readonly search: string;
  readonly segment: string;
  readonly noSegment: boolean;
  readonly city: string;
  readonly source: SourceType | '';
  readonly focus: CompanyFocus | '';
  readonly sort: CompanySort;
  readonly direction: 'asc' | 'desc';
}

/** Lead mais relevante da empresa: o aberto, ou o último encerrado. */
export interface CurrentLead {
  readonly leadId: string;
  readonly status: LeadStatus;
  readonly open: boolean;
  readonly assigneeName?: string;
}

/** Linha da lista de empresas (D32): diz se tem lead, como contatar e se parece duplicata. */
export interface CompanyWorklistItem {
  readonly companyId: string;
  readonly name: string;
  readonly tradeName?: string;
  readonly segment?: string;
  readonly city: string;
  readonly state: string;
  readonly hasPhone: boolean;
  readonly hasEmail: boolean;
  readonly hasWebsite: boolean;
  readonly hasInstagram: boolean;
  readonly origin?: SourceType;
  readonly currentLead?: CurrentLead;
  readonly leadCount: number;
  readonly lastActivityAt?: string;
  readonly possibleDuplicate: boolean;
  readonly archived: boolean;
  readonly createdAt: string;
}

/** Um ciclo comercial da empresa. */
export interface LeadCycle {
  readonly leadId: string;
  readonly status: LeadStatus;
  readonly priority: LeadPriority;
  readonly score?: number;
  readonly assigneeName?: string;
  readonly estimatedValue?: number;
  readonly createdAt: string;
  readonly closedAt?: string;
}

/** Se a exclusão seria aceita agora (D32). */
export interface RemovalEligibility {
  readonly allowed: boolean;
  readonly blocker?: 'HAS_ACTIVITY' | 'NOT_ALLOWED';
  /** Prazo de quem cadastrou; ausente para o administrador. */
  readonly allowedUntil?: string;
}

export interface MergeResult {
  readonly company: Company;
  readonly filledFields: readonly string[];
  readonly movedLeads: number;
}

export interface CompanyBulkResult {
  readonly updated: number;
  readonly skippedArchived: number;
}

/** O que o formulário já tem para procurar duplicata enquanto se digita. */
export interface DuplicateProbe {
  readonly name: string;
  readonly city: string;
  readonly state: string;
  readonly websiteUrl: string;
  readonly phone: string;
  readonly excludeCompanyId?: string;
}
