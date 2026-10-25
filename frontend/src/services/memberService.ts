import type { Member, Page } from '../types/member';
import { request } from './httpClient';

/**
 * Membros da organizacao ativa. Apenas administradores; operador recebe 403.
 *
 * A organizacao nao e informada aqui de proposito: vem sempre da sessao no
 * backend (RF-002).
 */
export function fetchMembers(signal?: AbortSignal): Promise<Page<Member>> {
  return request<Page<Member>>('/organizations/members', signal ? { signal } : {});
}
