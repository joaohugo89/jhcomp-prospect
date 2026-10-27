import type { Member } from '../types/member';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/**
 * Membros da organizacao ativa. Apenas administradores; operador recebe 403.
 *
 * A organizacao nao e informada aqui de proposito: vem sempre da sessao no
 * backend (RF-002).
 */
export function fetchMembers(page: number, signal?: AbortSignal): Promise<Page<Member>> {
  return request<Page<Member>>(`/organizations/members${toQuery({ page })}`, signal ? { signal } : {});
}
