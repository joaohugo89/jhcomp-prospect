import type { LeadPriority, LeadStatus } from './lead';

/** Mirrors the backend's CompanyResponses (doc 05 §4). Optional fields are absent when null. */
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

/** Editable fields. An empty string means "no value". */
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

/** One-click slices of the company list. Without ARCHIVED, only active ones. */
export const COMPANY_FOCUS = ['WITHOUT_LEAD', 'WITHOUT_CONTACT', 'DUPLICATE', 'ARCHIVED'] as const;
export type CompanyFocus = (typeof COMPANY_FOCUS)[number];

export const COMPANY_SORT = ['NAME', 'CITY', 'CREATED', 'LAST_ACTIVITY'] as const;
export type CompanySort = (typeof COMPANY_SORT)[number];

/** The company list's filters, in their URL form. */
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

/** The company's most relevant lead: the open one, or the last closed one. */
export interface CurrentLead {
  readonly leadId: string;
  readonly status: LeadStatus;
  readonly open: boolean;
  readonly assigneeName?: string;
}

/** A row of the company list (D32): whether it has a lead, how to reach it, whether it looks duplicate. */
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

/** One commercial cycle of the company. */
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

/** Whether deletion would be accepted right now (D32). */
export interface RemovalEligibility {
  readonly allowed: boolean;
  readonly blocker?: 'HAS_ACTIVITY' | 'NOT_ALLOWED';
  /** The registrant's deadline; absent for an administrator. */
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

/** What the form already has for looking up duplicates while typing. */
export interface DuplicateProbe {
  readonly name: string;
  readonly city: string;
  readonly state: string;
  readonly websiteUrl: string;
  readonly phone: string;
  readonly excludeCompanyId?: string;
}
