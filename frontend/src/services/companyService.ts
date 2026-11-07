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

/** Companies (doc 05 §4). The organisation comes from the session on the backend, never from here. */

export function getCompany(id: string, signal?: AbortSignal): Promise<Company> {
  return request<Company>(`/companies/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

/** The origin is optional: with no name, the backend records a manual entry. */
export function createCompany(input: CompanyInput, source: SourceInput): Promise<Company> {
  const hasSource = source.name.trim() !== '';
  return request<Company>('/companies', {
    method: 'POST',
    body: hasSource ? { ...input, source } : input,
  });
}

/** Sends every field: an empty one clears an optional value, per the PATCH contract. */
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

/** The company list as a tool: current lead, contact, origin, activity and duplicate (D32). */
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

/** Permanent deletion; the typed name confirms the intent, as in the dialog. */
export function deleteCompany(id: string, confirmationName: string): Promise<void> {
  return request<void>(`/companies/${encodeURIComponent(id)}`, { method: 'DELETE', body: { confirmationName } });
}

/** Company `targetId` absorbs `sourceId`, which ends up archived. */
export function mergeCompanies(targetId: string, sourceId: string): Promise<MergeResult> {
  return request<MergeResult>(`/companies/${encodeURIComponent(targetId)}/merge`, {
    method: 'POST',
    body: { sourceCompanyId: sourceId },
  });
}

/** A POST, so the phone and the site never reach the URL or an access log. */
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
