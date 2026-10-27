import type {
  Company,
  CompanyFilters,
  CompanyInput,
  DuplicateCandidate,
  Source,
  SourceInput,
} from '../types/company';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Empresas (doc 05 §4). A organização vem da sessão no backend, nunca daqui. */

export function listCompanies(
  filters: CompanyFilters,
  page: number,
  signal?: AbortSignal,
): Promise<Page<Company>> {
  const query = toQuery({
    search: filters.search.trim(),
    segment: filters.segment.trim(),
    city: filters.city.trim(),
    includeArchived: filters.includeArchived,
    page,
  });
  return request<Page<Company>>(`/companies${query}`, signal ? { signal } : {});
}

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
