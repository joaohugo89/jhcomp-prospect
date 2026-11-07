export type OrganizationRole = 'ADMIN' | 'OPERATOR';

/** An active member as colleagues see them (`/team`): no e-mail, no role. */
export interface Teammate {
  readonly userId: string;
  readonly displayName: string;
}

/** The response of /auth/login and /auth/me. */
export interface Session {
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly organizationId: string;
  readonly role: OrganizationRole;
  /** Temporary password: only the password change is allowed until it happens (D28). */
  readonly passwordChangeRequired: boolean;
}
