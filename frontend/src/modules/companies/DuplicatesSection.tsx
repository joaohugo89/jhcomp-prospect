import { DUPLICATE_SIGNAL_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { LoadStateView } from '../../components/States';
import { link } from '../../components/styles';
import { listDuplicates } from '../../services/companyService';

/**
 * Possíveis duplicatas (RF-014).
 *
 * Só sugere e leva à outra empresa para comparação: nada é mesclado nem
 * descartado a partir daqui.
 */
export function DuplicatesSection({ companyId }: { readonly companyId: string }): React.JSX.Element {
  const [state, reload] = useLoad(companyId, (signal) => listDuplicates(companyId, signal));

  return (
    <section aria-labelledby="duplicatas" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="duplicatas" className="text-base font-semibold">
        Possíveis duplicatas
      </h2>
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.length === 0 ? (
        <p className="mt-2 text-sm text-slate-600">Nenhuma empresa com o mesmo domínio, telefone ou nome nesta cidade.</p>
      ) : (
        <>
          <p className="mt-1 text-sm text-slate-600">Revise antes de seguir: são sugestões, não confirmações.</p>
          <ul className="mt-3 space-y-3">
            {state.data.map((candidate) => (
              <li key={candidate.companyId} className="text-sm">
                <Link to={`/companies/${candidate.companyId}`} className={link}>
                  {candidate.name}
                </Link>
                <span className="text-slate-600">
                  {' '}
                  · {candidate.city}/{candidate.state}
                  {candidate.archived ? ' · arquivada' : ''}
                </span>
                <ul className="mt-1 flex flex-wrap gap-1">
                  {candidate.signals.map((signal) => (
                    <li key={signal} className="rounded bg-amber-50 px-1.5 py-0.5 text-xs text-amber-900 ring-1 ring-amber-300">
                      {DUPLICATE_SIGNAL_LABEL[signal]}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
