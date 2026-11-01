import type { Page } from '../types/page';
import type { Teammate } from '../types/session';
import { request } from './httpClient';

/** Membros ativos, só id e nome: para atribuir lead e filtrar por responsável. */
export function getTeam(signal?: AbortSignal): Promise<Page<Teammate>> {
  return request<Page<Teammate>>('/team?size=100', signal ? { signal } : {});
}
