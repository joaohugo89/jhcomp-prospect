import type { DuplicateSignal } from './company';

/** Campos que uma coluna do CSV pode preencher. Mesmas chaves do cadastro de empresa. */
export const IMPORT_FIELDS = [
  'name',
  'tradeName',
  'segment',
  'city',
  'state',
  'websiteUrl',
  'instagramUrl',
  'phone',
  'businessEmail',
  'address',
  'notes',
] as const;

export type ImportField = (typeof IMPORT_FIELDS)[number];

export const REQUIRED_IMPORT_FIELDS: ReadonlySet<ImportField> = new Set(['name', 'city', 'state']);

export type ImportStatus = 'ANALYZED' | 'IMPORTING' | 'COMPLETED' | 'DISCARDED';

export type ImportRowStatus = 'VALID' | 'REJECTED' | 'DUPLICATE' | 'IMPORTED' | 'MERGED' | 'SKIPPED';

/** Escolha do operador para uma possível duplicata. Sem escolha, a linha não é importada. */
export type DuplicateChoice = 'new' | 'merge';

/** Espelha ImportResponse do backend (doc 05 §6, D23). */
export interface ImportBatch {
  readonly id: string;
  readonly fileName: string;
  readonly sourceName: string;
  readonly datasetNote?: string;
  readonly status: ImportStatus;
  readonly columnMapping: Readonly<Partial<Record<ImportField, string>>>;
  readonly totalRows: number;
  readonly validRows: number;
  readonly rejectedRows: number;
  readonly duplicateRows: number;
  readonly importedRows: number;
  readonly mergedRows: number;
  readonly leadsCreated: number;
  readonly createdAt: string;
  readonly completedAt?: string;
  readonly ignoredColumns?: readonly string[];
  readonly previousImportId?: string;
}

export interface ImportRowError {
  readonly field: string;
  readonly message: string;
}

export interface ImportRow {
  readonly rowNumber: number;
  readonly status: ImportRowStatus;
  readonly values: Readonly<Partial<Record<ImportField, string>>>;
  readonly errors: readonly ImportRowError[];
  readonly duplicateOfCompanyId?: string;
  readonly duplicateOfRowNumber?: number;
  readonly duplicateSignals: readonly DuplicateSignal[];
  readonly companyId?: string;
  readonly leadId?: string;
  /** Campos que a linha preencheu na empresa existente. */
  readonly mergedFields?: readonly ImportField[];
}

export interface UploadInput {
  readonly file: File;
  readonly sourceName: string;
  readonly datasetNote: string;
  /** Vazio: o backend reconhece os cabeçalhos sozinho. */
  readonly mapping: Readonly<Partial<Record<ImportField, string>>>;
}
