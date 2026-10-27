import { useState } from 'react';

import { formatDateTime, safeHref } from '../../app/format';
import { SOURCE_TYPE_LABEL } from '../../app/labels';
import { useLoad } from '../../app/useLoad';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { link } from '../../components/styles';
import { listCompanySources } from '../../services/companyService';

/** De onde vieram os dados da empresa e quando (RF-022). */
export function SourcesSection({ companyId }: { readonly companyId: string }): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${companyId}:${page}`, (signal) => listCompanySources(companyId, page, signal));

  return (
    <section aria-labelledby="origem" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="origem" className="text-base font-semibold">
        Origem dos dados
      </h2>
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : (
        <>
          <ul className="mt-3 divide-y divide-slate-100">
            {state.data.content.map((source) => {
              const href = safeHref(source.url);
              return (
                <li key={source.id} className="py-2 text-sm">
                  <p className="font-medium text-slate-900">
                    {source.name}{' '}
                    <span className="font-normal text-slate-500">· {SOURCE_TYPE_LABEL[source.type]}</span>
                  </p>
                  <p className="text-slate-600">
                    Coletado em {formatDateTime(source.collectedAt)}
                    {source.leadId === undefined ? '' : ' · ligado a uma oportunidade'}
                  </p>
                  {source.reference !== undefined && <p className="text-slate-600">{source.reference}</p>}
                  {href !== undefined && (
                    <a href={href} target="_blank" rel="noopener noreferrer" className={`${link} break-all`}>
                      {source.url}
                    </a>
                  )}
                </li>
              );
            })}
          </ul>
          <Pagination page={state.data} onChange={setPage} />
        </>
      )}
    </section>
  );
}
