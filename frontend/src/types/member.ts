import type { OrganizationRole } from './session';

export type MemberStatus = 'ACTIVE' | 'REVOKED';

export interface Member {
  readonly memberId: string;
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: OrganizationRole;
  readonly status: MemberStatus;
  /** A temporary password not yet changed: the person has not signed in for the first time. */
  readonly passwordChangeRequired: boolean;
  /** Last login in this organisation; absent when they never signed in. */
  readonly lastLoginAt?: string;
  readonly createdAt: string;
}

/** An overview of the team. */
export interface TeamSummary {
  readonly active: number;
  readonly admins: number;
  readonly revoked: number;
  readonly awaitingFirstAccess: number;
}

/** The create and reset response: the temporary password appears only here (D28). */
export interface MemberWithPassword {
  readonly member: Member;
  readonly temporaryPassword: string;
}
