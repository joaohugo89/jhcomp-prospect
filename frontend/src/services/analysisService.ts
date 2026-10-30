import type { AnalysisDetail, AnalysisSummary, FindingStatus } from '../types/analysis';
import type { Page } from '../types/page';
import { request, toQuery } from './httpClient';

/** Análises (doc 05 §8). O pedido só enfileira; o resultado chega depois. */

export function requestAnalysis(leadId: string): Promise<AnalysisSummary> {
  return request<AnalysisSummary>(`/leads/${encodeURIComponent(leadId)}/analyses`, { method: 'POST' });
}

/** Mais recente primeiro. */
export function listAnalyses(leadId: string, page: number, signal?: AbortSignal): Promise<Page<AnalysisSummary>> {
  return request<Page<AnalysisSummary>>(
    `/leads/${encodeURIComponent(leadId)}/analyses${toQuery({ page, size: 1 })}`,
    signal ? { signal } : {},
  );
}

export function getAnalysis(id: string, signal?: AbortSignal): Promise<AnalysisDetail> {
  return request<AnalysisDetail>(`/analyses/${encodeURIComponent(id)}`, signal ? { signal } : {});
}

export function reviewFinding(findingId: string, status: FindingStatus, note: string): Promise<AnalysisDetail> {
  return request<AnalysisDetail>(`/findings/${encodeURIComponent(findingId)}/review`, {
    method: 'PATCH',
    body: { status, note: note.trim() === '' ? undefined : note.trim() },
  });
}
