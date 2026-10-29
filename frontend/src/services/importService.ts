import type { ImportBatch, ImportRow, ImportRowStatus, UploadInput } from '../types/import';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Importação CSV em duas fases (doc 05 §6, D23). Nada vira empresa sem `confirmImport`. */

/** Análise pode levar alguns segundos num arquivo no limite da D14. */
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

export function listImports(page: number, signal?: AbortSignal): Promise<Page<ImportBatch>> {
  return request<Page<ImportBatch>>(`/imports${toQuery({ page })}`, signal ? { signal } : {});
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
 * @param includeDuplicateRows duplicatas a importar como empresa nova
 * @param mergeDuplicateRows duplicatas que completam os campos vazios da empresa existente
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
    // Confirmar cria uma empresa por linha numa única transação.
    timeoutMs: ANALYSIS_TIMEOUT_MS,
  });
}

export function discardImport(id: string): Promise<ImportBatch> {
  return request<ImportBatch>(`/imports/${encodeURIComponent(id)}/discard`, { method: 'POST' });
}
