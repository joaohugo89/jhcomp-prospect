import type { Health } from '../types/health';
import { request } from './httpClient';

export function fetchHealth(signal?: AbortSignal): Promise<Health> {
  return request<Health>('/health', signal ? { signal } : {});
}
