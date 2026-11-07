import { useEffect, useEffectEvent, useRef, useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { ANALYSIS_STATUS_LABEL, PRIORITY_LABEL } from '../../app/labels';
import { useLoad } from '../../app/useLoad';
import { LoadStateView } from '../../components/States';
import { buttonPrimary } from '../../components/styles';
import { getAnalysis, listAnalyses, requestAnalysis } from '../../services/analysisService';
import type { AnalysisDetail, AnalysisSummary } from '../../types/analysis';
import type { Lead, LeadPriority } from '../../types/lead';
import { FindingRow } from './FindingRow';

/** Interval between polls while the analysis runs in the background. */
const POLL_MS = 3000;

/**
 * The suggested bands from doc 06 §7. A suggestion only: priority remains the
 * operator's decision, and the bands have not been calibrated against real
 * outcomes yet.
 */
function suggestedPriority(score: number): LeadPriority {
  if (score >= 80) {
    return 'VERY_HIGH';
  }
  if (score >= 60) {
    return 'HIGH';
  }
  return score >= 30 ? 'MEDIUM' : 'LOW';
}

function isActive(analysis: AnalysisSummary): boolean {
  return analysis.status === 'PENDING' || analysis.status === 'PROCESSING';
}

/**
 * The lead's digital presence (doc 08 §7): request, progress, findings and
 * the score breakdown (RF-030 to RF-033).
 */
export function AnalysisSection({
  lead,
  onScoreChanged,
}: {
  readonly lead: Lead;
  readonly onScoreChanged: () => void;
}): React.JSX.Element {
  const [state, reload] = useLoad(lead.id, (signal) => listAnalyses(lead.id, 0, signal));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const latest = state.kind === 'ready' ? state.data.content[0] : undefined;
  const active = latest !== undefined && isActive(latest);

  // Follows a running analysis by polling at intervals.
  useEffect(() => {
    if (!active) {
      return undefined;
    }
    const timer = window.setInterval(reload, POLL_MS);
    return () => {
      window.clearInterval(timer);
    };
  }, [active, reload]);

  // On completion the lead's score changed on the server, so tell the page.
  // Only on the transition from running to completed, never during rendering.
  const notifyScoreChanged = useEffectEvent(onScoreChanged);
  const wasActive = useRef(false);
  const completed = latest?.status === 'COMPLETED';
  useEffect(() => {
    if (wasActive.current && !active && completed) {
      notifyScoreChanged();
    }
    wasActive.current = active;
  }, [active, completed]);

  async function analyze(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await requestAnalysis(lead.id);
      reload();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'Lead marcado como não contatar não é analisado.'));
    } finally {
      setBusy(false);
    }
  }

  const doNotContact = lead.status === 'DO_NOT_CONTACT';

  return (
    <section aria-labelledby="presenca" className="rounded-2xl border border-stone-200/70 glass p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="presenca" className="text-base font-semibold">
            Presença digital
          </h2>
          <p className="mt-1 text-xs text-stone-500">
            Verifica o site cadastrado: HTTPS, adaptação a celular e links de contato da página inicial.
          </p>
        </div>
        <button
          type="button"
          className={buttonPrimary}
          disabled={busy || active || doNotContact}
          onClick={() => {
            void analyze();
          }}
        >
          {latest === undefined ? 'Analisar' : 'Analisar de novo'}
        </button>
      </div>

      {doNotContact && <p className="mt-3 text-sm text-stone-600">Lead marcado como não contatar: não é analisado.</p>}
      {error !== null && (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : latest === undefined ? (
        <p className="mt-4 text-sm text-stone-600">Nenhuma análise ainda. Sem análise, o lead não tem score.</p>
      ) : (
        <LatestAnalysis summary={latest} onScoreChanged={onScoreChanged} />
      )}
    </section>
  );
}

function LatestAnalysis({
  summary,
  onScoreChanged,
}: {
  readonly summary: AnalysisSummary;
  readonly onScoreChanged: () => void;
}): React.JSX.Element {
  if (isActive(summary)) {
    return (
      <p role="status" className="mt-4 text-sm text-stone-700">
        {ANALYSIS_STATUS_LABEL[summary.status]} desde {formatDateTime(summary.createdAt)}. A página atualiza sozinha.
      </p>
    );
  }
  if (summary.status === 'FAILED') {
    return (
      <p role="alert" className="mt-4 text-sm text-red-800">
        A análise de {formatDateTime(summary.createdAt)} falhou: {summary.failureReason ?? 'motivo não informado'}.
        Tente de novo.
      </p>
    );
  }
  return <CompletedAnalysis key={summary.id} analysisId={summary.id} onScoreChanged={onScoreChanged} />;
}

function CompletedAnalysis({
  analysisId,
  onScoreChanged,
}: {
  readonly analysisId: string;
  readonly onScoreChanged: () => void;
}): React.JSX.Element {
  const [state, reload] = useLoad(analysisId, (signal) => getAnalysis(analysisId, signal));
  const [reviewed, setReviewed] = useState<AnalysisDetail | null>(null);

  if (state.kind !== 'ready') {
    return <LoadStateView state={state} onRetry={reload} />;
  }
  const detail = reviewed ?? state.data;
  const score = detail.analysis.score ?? 0;

  return (
    <div className="mt-4">
      <p className="text-sm text-stone-700">
        <span className="text-2xl font-semibold tabular-nums text-stone-900">{score}</span>
        <span className="text-stone-500"> / 100</span> · fórmula {detail.analysis.scoreVersion} · concluída em{' '}
        {detail.analysis.completedAt === undefined ? '—' : formatDateTime(detail.analysis.completedAt)}
      </p>
      <p className="mt-1 text-xs text-stone-500">
        Prioridade sugerida pelas faixas do score: {PRIORITY_LABEL[suggestedPriority(score)]}. O score indica
        oportunidade de serviço, não probabilidade de compra. Só achados confirmados pontuam.
      </p>
      <ol className="mt-3 divide-y divide-stone-100">
        {detail.findings.map((finding) => (
          <FindingRow
            key={finding.id}
            finding={finding}
            onReviewed={(next) => {
              setReviewed(next);
              onScoreChanged();
            }}
          />
        ))}
      </ol>
    </div>
  );
}
