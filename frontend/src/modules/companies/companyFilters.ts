import { COMPANY_FOCUS, COMPANY_SORT, SOURCE_TYPES, type CompanyWorklistFilters } from '../../types/company';

/**
 * The company list's filters and their shape in the URL: back, reload and
 * bookmarking all keep the current slice.
 */

export const EMPTY_COMPANY_FILTERS: CompanyWorklistFilters = {
  search: '',
  segment: '',
  noSegment: false,
  city: '',
  source: '',
  focus: '',
  sort: 'CREATED',
  direction: 'desc',
};

function pick<T extends string>(options: readonly T[], value: string | null): T | '' {
  return options.find((option) => option === value) ?? '';
}

/** Reads the query string; an unknown value is ignored, never an error. */
export function companyFiltersFromQuery(query: string): CompanyWorklistFilters {
  const params = new URLSearchParams(query);
  const text = (key: string): string => params.get(key)?.trim() ?? '';
  const sort = pick(COMPANY_SORT, params.get('sort'));
  return {
    search: text('search'),
    segment: text('segment'),
    noSegment: params.get('noSegment') === 'true',
    city: text('city'),
    source: pick(SOURCE_TYPES, params.get('source')),
    focus: pick(COMPANY_FOCUS, params.get('focus')),
    sort: sort === '' ? EMPTY_COMPANY_FILTERS.sort : sort,
    direction: params.get('direction') === 'asc' ? 'asc' : 'desc',
  };
}

const FIELDS: readonly (keyof CompanyWorklistFilters)[] = [
  'search',
  'segment',
  'noSegment',
  'city',
  'source',
  'focus',
  'sort',
  'direction',
];

/** A query string holding only what differs from the default. */
export function companyFiltersToQuery(filters: CompanyWorklistFilters): string {
  const params = new URLSearchParams();
  for (const field of FIELDS) {
    const value = filters[field];
    if (value === EMPTY_COMPANY_FILTERS[field]) {
      continue;
    }
    if (typeof value === 'boolean') {
      params.set(field, String(value));
    } else if (value.trim() !== '') {
      params.set(field, value.trim());
    }
  }
  return params.toString();
}

/** Any filter beyond sorting: distinguishes "nothing registered" from "nothing found". */
export function isCompanyFiltered(filters: CompanyWorklistFilters): boolean {
  return (
    companyFiltersToQuery({
      ...filters,
      sort: EMPTY_COMPANY_FILTERS.sort,
      direction: EMPTY_COMPANY_FILTERS.direction,
    }) !== ''
  );
}
