export type OrganizationRole = 'ADMIN' | 'OPERATOR';

/** Resposta de /auth/login e /auth/me. */
export interface Session {
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly organizationId: string;
  readonly role: OrganizationRole;
  /** Senha temporária: só a troca de senha é permitida até ela acontecer (D28). */
  readonly passwordChangeRequired: boolean;
}
