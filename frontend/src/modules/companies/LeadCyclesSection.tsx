import { useState } from 'react';

import { formatCurrency, formatDateTime } from '../../app/format';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { StatusBadge } from '../../components/Badges';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { card, link } from '../../components/styles';
import { listCompanyLeads } from '../../services/companyService';

/** Todos os ciclos comerciais da empresa, do mais recente ao mais antigo. */
export function LeadCyclesSection({
  companyId,
  refreshKey,
}: {
  readonly companyId: string;
  readonly refreshKey: string;
}): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${companyId}|${page}|${refreshKey}`, (signal) =>
    listCompanyLeads(companyId, page, signal),
  );

  return (
    <section aria-labelledby="ciclos" className={`${card} p-4`}>
      <h2 id="ciclos" className="text-sm font-semibold text-slate-900">
        Ciclos comerciais
      </h2>
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.content.length === 0 ? (
        <p className="mt-2 text-sm text-slate-600">Nenhum lead aberto para esta empresa ainda.</p>
      ) : (
        <>
          <ol className="mt-3 divide-y divide-slate-100">
            {state.data.content.map((cycle) => (
              <li key={cycle.leadId} className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <StatusBadge status={cycle.status} />
                    <Link to={`/leads/${cycle.leadId}`} className={link}>
                      Abrir lead
                    </Link>
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {`Aberto em ${formatDateTime(cycle.createdAt)}${
                      cycle.closedAt === undefined ? '' : ` · encerrado em ${formatDateTime(cycle.closedAt)}`
                    } · ${cycle.assigneeName ?? 'sem responsável'}`}
                  </p>
                </div>
                <span className="text-sm font-semibold text-slate-900 tabular-nums">
                  {cycle.estimatedValue === undefined ? (
                    <span className="font-normal text-slate-400">Sem valor</span>
                  ) : (
                    formatCurrency(cycle.estimatedValue)
                  )}
                </span>
              </li>
            ))}
          </ol>
          <Pagination page={state.data} onChange={setPage} />
        </>
      )}
    </section>
  );
}
