import type { Member, MemberStatus, MemberWithPassword } from '../types/member';
import type { Page } from '../types/page';
import type { OrganizationRole } from '../types/session';
import { request, toQuery } from './httpClient';

/**
 * Membros da organizacao ativa. Apenas administradores; operador recebe 403.
 *
 * A organizacao nao e informada aqui de proposito: vem sempre da sessao no
 * backend (RF-002).
 */
export function fetchMembers(page: number, signal?: AbortSignal): Promise<Page<Member>> {
  return request<Page<Member>>(
    `/organizations/members${toQuery({ page, includeRevoked: true })}`,
    signal ? { signal } : {},
  );
}

export function createMember(email: string, displayName: string, role: OrganizationRole): Promise<MemberWithPassword> {
  return request<MemberWithPassword>('/organizations/members', {
    method: 'POST',
    body: { email: email.trim(), displayName: displayName.trim(), role },
  });
}

export function updateMember(
  memberId: string,
  change: { readonly role?: OrganizationRole; readonly status?: MemberStatus },
): Promise<Member> {
  return request<Member>(`/organizations/members/${encodeURIComponent(memberId)}`, { method: 'PATCH', body: change });
}

/** Nova senha temporaria; as sessoes abertas do membro sao encerradas. */
export function resetMemberPassword(memberId: string): Promise<MemberWithPassword> {
  return request<MemberWithPassword>(`/organizations/members/${encodeURIComponent(memberId)}/reset-password`, {
    method: 'POST',
  });
}
