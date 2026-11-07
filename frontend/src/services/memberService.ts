import type { Member, MemberStatus, MemberWithPassword, TeamSummary } from '../types/member';
import type { Page } from '../types/page';
import type { OrganizationRole } from '../types/session';
import { request, toQuery } from './httpClient';

/**
 * Members of the active organisation. Administrators only; an operator gets
 * a 403.
 *
 * The organisation is deliberately not passed here: it always comes from the
 * session on the backend (RF-002).
 */
export function fetchMembers(
  filters: { readonly status: MemberStatus | ''; readonly search: string },
  page: number,
  signal?: AbortSignal,
): Promise<Page<Member>> {
  return request<Page<Member>>(
    `/organizations/members${toQuery({ page, includeRevoked: true, status: filters.status, search: filters.search.trim() })}`,
    signal ? { signal } : {},
  );
}

export function getTeamSummary(signal?: AbortSignal): Promise<TeamSummary> {
  return request<TeamSummary>('/organizations/members/summary', signal ? { signal } : {});
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

/** A new temporary password; the member's open sessions are ended. */
export function resetMemberPassword(memberId: string): Promise<MemberWithPassword> {
  return request<MemberWithPassword>(`/organizations/members/${encodeURIComponent(memberId)}/reset-password`, {
    method: 'POST',
  });
}
