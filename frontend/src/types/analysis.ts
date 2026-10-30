/** Análise de presença digital (doc 05 §8, D24). Espelha AnalysisController do backend. */

export const FINDING_STATUSES = ['CONFIRMED', 'NOT_FOUND', 'NOT_VERIFIED', 'NOT_APPLICABLE', 'CONTRADICTED'] as const;

export type FindingStatus = (typeof FINDING_STATUSES)[number];

export type AnalysisStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export type Criterion =
  | 'SITE_ABSENT'
  | 'NO_HTTPS'
  | 'NO_MOBILE_VIEWPORT'
  | 'NO_CONTACT_LINK'
  | 'NO_ONLINE_BOOKING_OR_CATALOG'
  | 'BUSINESS_INFO_SUFFICIENT'
  | 'SERVICE_FIT'
  | 'LOCATION_SERVED';

export type Confidence = 'LOW' | 'MEDIUM' | 'HIGH';

export type ReviewStatus = 'PENDING' | 'CONFIRMED' | 'CORRECTED';

export type EvidenceType =
  | 'HTTP_RESPONSE'
  | 'HTML_SIGNAL'
  | 'ROBOTS_TXT'
  | 'COMPANY_RECORD'
  | 'CONFIGURATION'
  | 'OPERATOR_NOTE';

/** `score` só existe com a análise concluída: pendente ou com falha não é zero (D7). */
export interface AnalysisSummary {
  readonly id: string;
  readonly leadId: string;
  readonly status: AnalysisStatus;
  readonly score?: number;
  readonly scoreVersion?: string;
  readonly failureReason?: string;
  readonly createdAt: string;
  readonly startedAt?: string;
  readonly completedAt?: string;
}

export interface Evidence {
  readonly type: EvidenceType;
  readonly referenceUrl?: string;
  readonly description: string;
  readonly observedAt: string;
  readonly capturedBy: string;
  readonly contentHash?: string;
}

export interface Finding {
  readonly id: string;
  readonly criterion: Criterion;
  readonly status: FindingStatus;
  readonly originalStatus: FindingStatus;
  readonly description: string;
  readonly confidence?: Confidence;
  readonly pointsAwarded: number;
  readonly maxPoints: number;
  readonly reviewStatus: ReviewStatus;
  readonly reviewedAt?: string;
  readonly reviewNote?: string;
  readonly evidence: readonly Evidence[];
}

export interface AnalysisDetail {
  readonly analysis: AnalysisSummary;
  readonly findings: readonly Finding[];
}
