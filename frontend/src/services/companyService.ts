import type {
  Company,
  CompanyBulkResult,
  CompanyInput,
  CompanyWorklistFilters,
  CompanyWorklistItem,
  DuplicateCandidate,
  DuplicateProbe,
  LeadCycle,
  MergeResult,
  RemovalEligibility,
  Source,
  SourceInput,
} from '../types/company';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Empresas (doc 05 §4). A organização vem da sessão no backend, nunca daqui. */

export function getCompany(id: string, signal?: AbortSignal): Promise<Company> {
  return request<Company>(`/companies/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

/** Origem é opcional: sem nome, o backend registra "Cadastro manual". */
export function createCompany(input: CompanyInput, source: SourceInput): Promise<Company> {
  const hasSource = source.name.trim() !== '';
  return request<Company>('/companies', {
    method: 'POST',
    body: hasSource ? { ...input, source } : input,
  });
}

/** Envia todos os campos: vazio limpa o opcional, conforme o contrato do PATCH. */
export function updateCompany(id: string, input: CompanyInput): Promise<Company> {
  return request<Company>(`/companies/${encodeURIComponent(id)}`, { method: 'PATCH', body: input });
}

export function archiveCompany(id: string): Promise<Company> {
  return request<Company>(`/companies/${encodeURIComponent(id)}/archive`, { method: 'POST' });
}

export function listCompanySources(id: string, page: number, signal?: AbortSignal): Promise<Page<Source>> {
  return request<Page<Source>>(
    `/companies/${encodeURIComponent(id)}/sources${toQuery({ page })}`,
    signal ? { signal } : {},
  );
}

export function listDuplicates(id: string, signal?: AbortSignal): Promise<readonly DuplicateCandidate[]> {
  return request<readonly DuplicateCandidate[]>(
    `/companies/${encodeURIComponent(id)}/duplicates`,
    signal ? { signal } : {},
  );
}

/** Lista de empresas como ferramenta: lead atual, contato, origem, atividade e duplicata (D32). */
export function listCompanyWorklist(
  filters: CompanyWorklistFilters,
  page: number,
  signal?: AbortSignal,
): Promise<Page<CompanyWorklistItem>> {
  const query = toQuery({
    search: filters.search.trim(),
    segment: filters.noSegment ? '' : filters.segment.trim(),
    noSegment: filters.noSegment ? true : undefined,
    city: filters.city.trim(),
    source: filters.source,
    focus: filters.focus,
    sort: filters.sort,
    direction: filters.direction,
    page,
  });
  return request<Page<CompanyWorklistItem>>(`/companies/worklist${query}`, signal ? { signal } : {});
}

export function getCompanySummary(id: string, signal?: AbortSignal): Promise<CompanyWorklistItem> {
  return request<CompanyWorklistItem>(`/companies/${encodeURIComponent(id)}/summary`, signal ? { signal } : {});
}

export function listCompanyLeads(id: string, page: number, signal?: AbortSignal): Promise<Page<LeadCycle>> {
  return request<Page<LeadCycle>>(
    `/companies/${encodeURIComponent(id)}/leads${toQuery({ page })}`,
    signal ? { signal } : {},
  );
}

export function getRemovalEligibility(id: string, signal?: AbortSignal): Promise<RemovalEligibility> {
  return request<RemovalEligibility>(`/companies/${encodeURIComponent(id)}/removal`, signal ? { signal } : {});
}

/** Exclusão definitiva; o nome digitado confirma a intenção, como no diálogo. */
export function deleteCompany(id: string, confirmationName: string): Promise<void> {
  return request<void>(`/companies/${encodeURIComponent(id)}`, { method: 'DELETE', body: { confirmationName } });
}

/** A empresa `targetId` absorve `sourceId`, que fica arquivada. */
export function mergeCompanies(targetId: string, sourceId: string): Promise<MergeResult> {
  return request<MergeResult>(`/companies/${encodeURIComponent(targetId)}/merge`, {
    method: 'POST',
    body: { sourceCompanyId: sourceId },
  });
}

/** Por POST: telefone e site não vão para a URL nem para log de acesso. */
export function checkDuplicates(probe: DuplicateProbe, signal?: AbortSignal): Promise<readonly DuplicateCandidate[]> {
  return request<readonly DuplicateCandidate[]>('/companies/duplicate-check', {
    method: 'POST',
    body: probe,
    ...(signal ? { signal } : {}),
  });
}

export type CompanyBulkChange = { readonly segment: string } | { readonly city: string; readonly state: string };

export function bulkUpdateCompanies(ids: readonly string[], change: CompanyBulkChange): Promise<CompanyBulkResult> {
  return request<CompanyBulkResult>('/companies/bulk', { method: 'POST', body: { companyIds: ids, ...change } });
}
