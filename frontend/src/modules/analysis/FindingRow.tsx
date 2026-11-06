import { useId, useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime, safeHref } from '../../app/format';
import { CONFIDENCE_LABEL, CRITERION_LABEL, EVIDENCE_TYPE_LABEL, FINDING_STATUS_LABEL } from '../../app/labels';
import { buttonSecondary, input, link } from '../../components/styles';
import { reviewFinding } from '../../services/analysisService';
import { FINDING_STATUSES, type AnalysisDetail, type Finding, type FindingStatus } from '../../types/analysis';

const STATUS_TONE: Readonly<Record<FindingStatus, string>> = {
  CONFIRMED: 'bg-amber-50 text-amber-900 ring-amber-300',
  NOT_FOUND: 'bg-stone-100 text-stone-700 ring-stone-300',
  NOT_VERIFIED: 'bg-stone-100 text-stone-700 ring-stone-300',
  NOT_APPLICABLE: 'glass text-stone-600 ring-stone-300',
  CONTRADICTED: 'bg-emerald-50 text-emerald-900 ring-emerald-300',
};

interface FindingRowProps {
  readonly finding: Finding;
  readonly onReviewed: (detail: AnalysisDetail) => void;
}

/**
 * Achado com evidência, pontos e revisão (RF-031, RF-032, RF-033).
 *
 * O estado aparece sempre em texto: "não verificado" nunca é mostrado como
 * fato confirmado (aceite do RF-031), e a cor só reforça.
 */
export function FindingRow({ finding, onReviewed }: FindingRowProps): React.JSX.Element {
  const [reviewing, setReviewing] = useState(false);

  return (
    <li className="py-3 text-sm">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-medium text-stone-900">{CRITERION_LABEL[finding.criterion]}</p>
        <p className="flex items-center gap-2">
          <span
            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${STATUS_TONE[finding.status]}`}
          >
            {FINDING_STATUS_LABEL[finding.status]}
          </span>
          <span className="font-mono text-xs tabular-nums text-stone-700">
            {finding.pointsAwarded}/{finding.maxPoints}
          </span>
        </p>
      </div>
      <p className="mt-1 text-stone-700">{finding.description}</p>
      <p className="mt-1 text-xs text-stone-500">
        {finding.confidence === undefined
          ? 'Sem confiança atribuída'
          : `Confiança ${CONFIDENCE_LABEL[finding.confidence]}`}
        {finding.reviewStatus === 'PENDING'
          ? ' · revisão pendente'
          : ` · revisado em ${finding.reviewedAt === undefined ? '—' : formatDateTime(finding.reviewedAt)}`}
        {finding.originalStatus !== finding.status &&
          ` · verificação automática: ${FINDING_STATUS_LABEL[finding.originalStatus]}`}
      </p>

      {finding.evidence.length > 0 && (
        <ul className="mt-2 space-y-1 border-l-2 border-stone-200 pl-3 text-xs text-stone-600">
          {finding.evidence.map((evidence) => {
            const href = safeHref(evidence.referenceUrl);
            return (
              <li key={`${evidence.type}-${evidence.observedAt}-${evidence.description}`}>
                <span className="font-medium">{EVIDENCE_TYPE_LABEL[evidence.type]}:</span> {evidence.description}{' '}
                {href !== undefined && (
                  <a href={href} target="_blank" rel="noopener noreferrer" className={`${link} break-all`}>
                    {evidence.referenceUrl}
                  </a>
                )}{' '}
                <time dateTime={evidence.observedAt}>({formatDateTime(evidence.observedAt)})</time>
              </li>
            );
          })}
        </ul>
      )}

      {reviewing ? (
        <ReviewForm
          finding={finding}
          onCancel={() => {
            setReviewing(false);
          }}
          onReviewed={(detail) => {
            setReviewing(false);
            onReviewed(detail);
          }}
        />
      ) : (
        <button
          type="button"
          className={`${link} mt-2 text-xs`}
          onClick={() => {
            setReviewing(true);
          }}
        >
          Revisar achado
        </button>
      )}
    </li>
  );
}

function ReviewForm({
  finding,
  onCancel,
  onReviewed,
}: {
  readonly finding: Finding;
  readonly onCancel: () => void;
  readonly onReviewed: (detail: AnalysisDetail) => void;
}): React.JSX.Element {
  const id = useId();
  const [status, setStatus] = useState<FindingStatus>(finding.status);
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onReviewed(await reviewFinding(finding.id, status, note));
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'A análise não aceita revisão no estado atual.'));
      setBusy(false);
    }
  }

  return (
    <form
      className="mt-2 space-y-2 rounded-md bg-stone-50 p-3"
      onSubmit={(event) => {
        void submit(event);
      }}
    >
      <label htmlFor={`${id}-status`} className="block text-xs font-medium text-stone-700">
        Estado após a revisão
      </label>
      <select
        id={`${id}-status`}
        className={input}
        value={status}
        onChange={(event) => {
          setStatus(FINDING_STATUSES.find((item) => item === event.target.value) ?? finding.status);
        }}
      >
        {FINDING_STATUSES.map((item) => (
          <option key={item} value={item}>
            {FINDING_STATUS_LABEL[item]}
          </option>
        ))}
      </select>
      <label htmlFor={`${id}-note`} className="block text-xs font-medium text-stone-700">
        Nota (vira evidência)
      </label>
      <textarea
        id={`${id}-note`}
        className={input}
        rows={2}
        maxLength={500}
        value={note}
        onChange={(event) => {
          setNote(event.target.value);
        }}
      />
      <div className="flex gap-2">
        <button type="submit" className={buttonSecondary} disabled={busy}>
          {busy ? 'Salvando…' : status === finding.status ? 'Confirmar estado' : 'Corrigir estado'}
        </button>
        <button type="button" className={buttonSecondary} onClick={onCancel} disabled={busy}>
          Cancelar
        </button>
      </div>
      {error !== null && (
        <p role="alert" className="text-xs text-red-700">
          {error}
        </p>
      )}
    </form>
  );
}
