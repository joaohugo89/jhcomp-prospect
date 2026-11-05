import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { DUPLICATE_SIGNAL_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { LoadStateView } from '../../components/States';
import { card, link } from '../../components/styles';
import { listDuplicates, mergeCompanies } from '../../services/companyService';
import type { Company, DuplicateCandidate, MergeResult } from '../../types/company';

/**
 * Possíveis duplicatas (RF-014) e mesclagem (D32). A sugestão não muda nada;
 * mesclar é decisão do operador, confirmada: a outra empresa passa leads e
 * origens para esta, completa campos vazios e fica arquivada.
 */
export function DuplicatesSection({
  company,
  onMerged,
}: {
  readonly company: Company;
  readonly onMerged: (result: MergeResult, sourceName: string) => void;
}): React.JSX.Element {
  const [state, reload] = useLoad(`${company.id}|${company.updatedAt}`, (signal) => listDuplicates(company.id, signal));
  const [merging, setMerging] = useState<DuplicateCandidate | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function merge(candidate: DuplicateCandidate): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      const result = await mergeCompanies(company.id, candidate.companyId);
      setMerging(null);
      onMerged(result, candidate.name);
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'As duas empresas têm lead aberto. Encerre um dos ciclos antes de mesclar.'));
      setMerging(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="duplicatas" className={`${card} p-4`}>
      <h2 id="duplicatas" className="text-sm font-semibold text-stone-900">
        Possíveis duplicatas
      </h2>
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.length === 0 ? (
        <p className="mt-2 text-sm text-stone-600">
          Nenhuma empresa com o mesmo domínio, telefone ou nome nesta cidade.
        </p>
      ) : (
        <>
          <p className="mt-1 text-sm text-stone-600">São sugestões, não confirmações. Compare antes de mesclar.</p>
          <ul className="mt-3 space-y-3">
            {state.data.map((candidate) => (
              <li key={candidate.companyId} className="text-sm">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <span>
                    <Link to={`/companies/${candidate.companyId}`} className={link}>
                      {candidate.name}
                    </Link>
                    <span className="text-stone-600">
                      {` · ${candidate.city}/${candidate.state}${candidate.archived ? ' · arquivada' : ''}`}
                    </span>
                  </span>
                  {!company.archived && !candidate.archived && (
                    <button
                      type="button"
                      className="text-xs font-semibold text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
                      onClick={() => {
                        setMerging(candidate);
                      }}
                    >
                      {`Mesclar ${candidate.name} nesta`}
                    </button>
                  )}
                </div>
                <ul className="mt-1 flex flex-wrap gap-1">
                  {candidate.signals.map((signal) => (
                    <li
                      key={signal}
                      className="rounded bg-amber-50 px-1.5 py-0.5 text-xs text-amber-900 ring-1 ring-amber-300"
                    >
                      {DUPLICATE_SIGNAL_LABEL[signal]}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </>
      )}
      {error !== null && (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}
        </p>
      )}
      {merging !== null && (
        <ConfirmDialog
          title={`Mesclar ${merging.name} em ${company.name}?`}
          description={`Leads, origens e linhas de importação de ${merging.name} passam para ${company.name}. Campos vazios de ${company.name} são completados, sem sobrescrever nada. ${merging.name} fica arquivada.`}
          confirmLabel="Mesclar"
          busy={busy}
          onConfirm={() => {
            void merge(merging);
          }}
          onCancel={() => {
            setMerging(null);
          }}
        />
      )}
    </section>
  );
}
