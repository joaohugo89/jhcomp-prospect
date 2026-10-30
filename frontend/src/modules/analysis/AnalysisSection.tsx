import { useEffect, useState } from 'react';

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

/** Intervalo entre consultas enquanto a análise roda em segundo plano. */
const POLL_MS = 3000;

/**
 * Faixas sugeridas do doc 06 §7. Só sugestão: a prioridade continua sendo
 * decisão do operador, e as faixas ainda não foram calibradas com dados reais.
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
 * Presença digital do lead (doc 08 §7): pedido, acompanhamento, achados e
 * decomposição do score (RF-030 a RF-033).
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

  // Acompanha a análise em andamento; ao concluir, o score do lead mudou.
  useEffect(() => {
    if (!active) {
      return undefined;
    }
    const timer = window.setTimeout(reload, POLL_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [active, latest?.id, latest?.status, reload, state]);

  const [lastSeen, setLastSeen] = useState<string | undefined>(undefined);
  const finishedKey = latest !== undefined && !isActive(latest) ? `${latest.id}:${latest.status}` : undefined;
  if (finishedKey !== lastSeen) {
    setLastSeen(finishedKey);
    if (lastSeen !== undefined && finishedKey !== undefined) {
      onScoreChanged();
    }
  }

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
    <section aria-labelledby="presenca" className="rounded-lg border border-slate-200 bg-white p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 id="presenca" className="text-base font-semibold">
            Presença digital
          </h2>
          <p className="mt-1 text-xs text-slate-500">
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

      {doNotContact && (
        <p className="mt-3 text-sm text-slate-600">Lead marcado como não contatar: não é analisado.</p>
      )}
      {error !== null && (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : latest === undefined ? (
        <p className="mt-4 text-sm text-slate-600">Nenhuma análise ainda. Sem análise, o lead não tem score.</p>
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
      <p role="status" className="mt-4 text-sm text-slate-700">
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
      <p className="text-sm text-slate-700">
        <span className="text-2xl font-semibold tabular-nums text-slate-900">{score}</span>
        <span className="text-slate-500"> / 100</span> · fórmula {detail.analysis.scoreVersion} · concluída em{' '}
        {detail.analysis.completedAt === undefined ? '—' : formatDateTime(detail.analysis.completedAt)}
      </p>
      <p className="mt-1 text-xs text-slate-500">
        Prioridade sugerida pelas faixas do score: {PRIORITY_LABEL[suggestedPriority(score)]}. O score indica
        oportunidade de serviço, não probabilidade de compra. Só achados confirmados pontuam.
      </p>
      <ol className="mt-3 divide-y divide-slate-100">
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
