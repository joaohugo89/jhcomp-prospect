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

export interface CompanyFilters {
  readonly search: string;
  readonly segment: string;
  readonly city: string;
  readonly includeArchived: boolean;
}
