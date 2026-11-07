import type { Page } from '../types/page';
import type { Teammate } from '../types/session';
import { request } from './httpClient';

/** Active members, id and name only: enough to assign a lead and filter by owner. */
export function getTeam(signal?: AbortSignal): Promise<Page<Teammate>> {
  return request<Page<Teammate>>('/team?size=100', signal ? { signal } : {});
}
