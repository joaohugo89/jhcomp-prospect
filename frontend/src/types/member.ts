import type { OrganizationRole } from './session';

export type MemberStatus = 'ACTIVE' | 'REVOKED';

export interface Member {
  readonly memberId: string;
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: OrganizationRole;
  readonly status: MemberStatus;
  /** Senha temporária ainda não trocada: a pessoa não fez o primeiro acesso. */
  readonly passwordChangeRequired: boolean;
  /** Último login nesta organização; ausente quando nunca entrou. */
  readonly lastLoginAt?: string;
  readonly createdAt: string;
}

/** Visão geral da equipe. */
export interface TeamSummary {
  readonly active: number;
  readonly admins: number;
  readonly revoked: number;
  readonly awaitingFirstAccess: number;
}

/** Resposta de cadastro e de redefinição: a senha temporária só aparece aqui (D28). */
export interface MemberWithPassword {
  readonly member: Member;
  readonly temporaryPassword: string;
}
