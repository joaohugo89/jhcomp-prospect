import type { DuplicateSignal } from './company';

/** The fields a CSV column can fill. The same keys as the company form. */
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

export type ImportRowStatus =
  | 'VALID'
  | 'REJECTED'
  | 'DUPLICATE'
  | 'IMPORTED'
  | 'MERGED'
  | 'SKIPPED'
  /** The company was deleted later: by mistake, or by undoing the import (D32). */
  | 'REMOVED';

/** The operator's choice for a possible duplicate. With no choice the row is not imported. */
export type DuplicateChoice = 'new' | 'merge';

/** Mirrors the backend's ImportResponse (doc 05 §6, D23). */
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
  /** The import was undone: the companies it created with no activity were deleted (D32). */
  readonly undoneAt?: string;
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
  /** The fields this row filled in on the existing company. */
  readonly mergedFields?: readonly ImportField[];
}

export interface UploadInput {
  readonly file: File;
  readonly sourceName: string;
  readonly datasetNote: string;
  /** Empty: the backend recognises the headers on its own. */
  readonly mapping: Readonly<Partial<Record<ImportField, string>>>;
}

/** What undoing the import would do (preview) or did (D32). */
export interface UndoResult {
  readonly removed: number;
  readonly keptWithActivity: number;
  readonly mergedUntouched: number;
  readonly undoneAt?: string;
}

/** An overview of the import history. */
export interface ImportSummary {
  readonly pendingReview: number;
  readonly completed: number;
  readonly undone: number;
  readonly discarded: number;
  /** Companies brought in by imports that still stand (not undone). */
  readonly companiesImported: number;
}
