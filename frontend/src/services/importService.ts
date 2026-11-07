import type {
  ImportBatch,
  ImportRow,
  ImportRowStatus,
  ImportStatus,
  ImportSummary,
  UndoResult,
  UploadInput,
} from '../types/import';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Two-phase CSV import (doc 05 §6, D23). Nothing becomes a company without `confirmImport`. */

/** Analysis can take a few seconds on a file at the D14 limit. */
const ANALYSIS_TIMEOUT_MS = 60_000;

export function analyzeCsv(input: UploadInput): Promise<ImportBatch> {
  const form = new FormData();
  form.append('file', input.file);
  if (input.sourceName.trim() !== '') {
    form.append('sourceName', input.sourceName.trim());
  }
  if (input.datasetNote.trim() !== '') {
    form.append('datasetNote', input.datasetNote.trim());
  }
  if (Object.keys(input.mapping).length > 0) {
    form.append('mapping', JSON.stringify(input.mapping));
  }
  return request<ImportBatch>('/imports/csv', { method: 'POST', body: form, timeoutMs: ANALYSIS_TIMEOUT_MS });
}

export function listImports(status: ImportStatus | '', page: number, signal?: AbortSignal): Promise<Page<ImportBatch>> {
  return request<Page<ImportBatch>>(`/imports${toQuery({ status, page })}`, signal ? { signal } : {});
}

export function getImportSummary(signal?: AbortSignal): Promise<ImportSummary> {
  return request<ImportSummary>('/imports/summary', signal ? { signal } : {});
}

export function getImport(id: string, signal?: AbortSignal): Promise<ImportBatch> {
  return request<ImportBatch>(`/imports/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

export function getImportReport(
  id: string,
  status: ImportRowStatus | '',
  page: number,
  signal?: AbortSignal,
): Promise<Page<ImportRow>> {
  return request<Page<ImportRow>>(
    `/imports/${encodeURIComponent(id)}/report${toQuery({ status, page })}`,
    signal ? { signal } : {},
  );
}

/**
 * @param includeDuplicateRows duplicates to import as new companies
 * @param mergeDuplicateRows duplicates that fill the empty fields of the existing company
 */
export function confirmImport(
  id: string,
  includeDuplicateRows: readonly number[],
  mergeDuplicateRows: readonly number[],
  createLeads: boolean,
): Promise<ImportBatch> {
  return request<ImportBatch>(`/imports/${encodeURIComponent(id)}/confirm`, {
    method: 'POST',
    body: { includeDuplicateRows, mergeDuplicateRows, createLeads },
    // Confirming creates one company per row inside a single transaction.
    timeoutMs: ANALYSIS_TIMEOUT_MS,
  });
}

/** Preview: how many companies would go, how many stay because they already have activity. Nothing changes. */
export function previewUndoImport(id: string, signal?: AbortSignal): Promise<UndoResult> {
  return request<UndoResult>(`/imports/${encodeURIComponent(id)}/undo`, signal ? { signal } : {});
}

/** Deletes the companies the import created that still have no activity (D32). */
export function undoImport(id: string): Promise<UndoResult> {
  return request<UndoResult>(`/imports/${encodeURIComponent(id)}/undo`, { method: 'POST' });
}

export function discardImport(id: string): Promise<ImportBatch> {
  return request<ImportBatch>(`/imports/${encodeURIComponent(id)}/discard`, { method: 'POST' });
}
