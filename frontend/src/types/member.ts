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

/** Envelope de listagem paginada devolvido pela API (RNF-002). */
export interface Page<T> {
  readonly content: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly hasNext: boolean;
}
