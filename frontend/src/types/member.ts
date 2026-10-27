import type { OrganizationRole } from './session';

export interface Member {
  readonly memberId: string;
  readonly userId: string;
  readonly email: string;
  readonly displayName: string;
  readonly role: OrganizationRole;
  readonly status: 'ACTIVE' | 'REVOKED';
  readonly createdAt: string;
}
