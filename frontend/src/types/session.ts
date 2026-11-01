export type OrganizationRole = 'ADMIN' | 'OPERATOR';

/** Membro ativo como os colegas o veem (`/team`): sem e-mail nem papel. */
export interface Teammate {
  readonly userId: string;
  readonly displayName: string;
}

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
