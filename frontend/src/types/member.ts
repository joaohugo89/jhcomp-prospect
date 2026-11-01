import type { OrganizationRole } from './session';

export type MemberStatus = 'ACTIVE' | 'REVOKED';

export interface Member {
  readonly memberId: string;
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: OrganizationRole;
  readonly status: MemberStatus;
  readonly createdAt: string;
}

/** Resposta de cadastro e de redefinição: a senha temporária só aparece aqui (D28). */
export interface MemberWithPassword {
  readonly member: Member;
  readonly temporaryPassword: string;
}
