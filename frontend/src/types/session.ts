export type OrganizationRole = 'ADMIN' | 'OPERATOR';

/** Resposta de /auth/login e /auth/me. */
export interface Session {
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly organizationId: string;
  readonly role: OrganizationRole;
}
