import { SOURCE_TYPES } from '../../types/company';
import { LEAD_PRIORITIES, LEAD_STATUSES } from '../../types/lead';
import { WORKLIST_FOCUS, WORKLIST_SORT, type WorklistFilters } from '../../types/worklist';

/**
 * The lead queue's filters and their shape in the URL. The same query string
 * serves the dashboard drill-down, bookmarking and the saved views.
 */

export const EMPTY_FILTERS: WorklistFilters = {
  search: '',
  status: '',
  priority: '',
  city: '',
  segment: '',
  noSegment: false,
  source: '',
  assignee: '',
  unassigned: false,
  scoreMin: '',
  scoreMax: '',
  focus: '',
  sort: 'UPDATED',
  direction: 'desc',
};

function pick<T extends string>(options: readonly T[], value: string | null): T | '' {
  return options.find((option) => option === value) ?? '';
}

/** Whole numbers from 0 to 100 only; anything else is ignored, as if absent. */
function score(value: string | null): string {
  const trimmed = value?.trim() ?? '';
  return /^\d{1,3}$/.test(trimmed) && Number(trimmed) <= 100 ? trimmed : '';
}

/** Reads the query string; an unknown value is ignored, never an error. */
export function filtersFromQuery(query: string): WorklistFilters {
  const params = new URLSearchParams(query);
  const text = (key: string): string => params.get(key)?.trim() ?? '';
  const sort = pick(WORKLIST_SORT, params.get('sort'));
  return {
    search: text('search'),
    status: pick(LEAD_STATUSES, params.get('status')),
    priority: pick(LEAD_PRIORITIES, params.get('priority')),
    city: text('city'),
    segment: text('segment'),
    noSegment: params.get('noSegment') === 'true',
    source: pick(SOURCE_TYPES, params.get('source')),
    assignee: text('assignee'),
    unassigned: params.get('unassigned') === 'true',
    scoreMin: score(params.get('scoreMin')),
    scoreMax: score(params.get('scoreMax')),
    focus: pick(WORKLIST_FOCUS, params.get('focus')),
    sort: sort === '' ? EMPTY_FILTERS.sort : sort,
    direction: params.get('direction') === 'asc' ? 'asc' : 'desc',
  };
}

/** A query string holding only what differs from the default: short and readable. */
const FIELDS: readonly (keyof WorklistFilters)[] = [
  'search',
  'status',
  'priority',
  'city',
  'segment',
  'noSegment',
  'source',
  'assignee',
  'unassigned',
  'scoreMin',
  'scoreMax',
  'focus',
  'sort',
  'direction',
];

export function filtersToQuery(filters: WorklistFilters): string {
  const params = new URLSearchParams();
  for (const field of FIELDS) {
    const value = filters[field];
    if (value === EMPTY_FILTERS[field]) {
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
export function isFiltered(filters: WorklistFilters): boolean {
  return filtersToQuery({ ...filters, sort: EMPTY_FILTERS.sort, direction: EMPTY_FILTERS.direction }) !== '';
}
