import { useState } from 'react';

import { formatDateTime, safeHref } from '../../app/format';
import { SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { useLoad } from '../../app/useLoad';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { link } from '../../components/styles';
import { getLeadTimeline } from '../../services/leadService';
import type { TimelineEntry } from '../../types/lead';

/**
 * Histórico do lead (RF-013): cronológico e somente leitura.
 *
 * `refreshKey` muda quando o lead é alterado nesta tela, para que o evento
 * recém-criado apareça sem recarregar a página.
 */
export function ActivityTimeline({
  leadId,
  refreshKey,
}: {
  readonly leadId: string;
  readonly refreshKey: string;
}): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${leadId}:${page}:${refreshKey}`, (signal) =>
    getLeadTimeline(leadId, page, signal),
  );

  return (
    <section aria-labelledby="historico" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="historico" className="text-base font-semibold">
        Histórico
      </h2>
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : (
        <>
          <ol className="mt-3 space-y-3 border-l-2 border-slate-200 pl-4">
            {state.data.content.map((entry) => (
              <li key={`${entry.type}-${entry.id}`} className="text-sm">
                <TimelineItem entry={entry} />
              </li>
            ))}
          </ol>
          <Pagination page={state.data} onChange={setPage} />
        </>
      )}
    </section>
  );
}

function TimelineItem({ entry }: { readonly entry: TimelineEntry }): React.JSX.Element {
  const when = (
    <p className="text-xs text-slate-500">
      <time dateTime={entry.occurredAt}>{formatDateTime(entry.occurredAt)}</time>
      {entry.actorName === undefined ? '' : ` · ${entry.actorName}`}
    </p>
  );

  if (entry.type === 'SOURCE') {
    const href = safeHref(entry.sourceUrl);
    return (
      <>
        <p className="text-slate-900">
          Origem: {entry.sourceName}
          {entry.sourceType === undefined ? '' : ` (${SOURCE_TYPE_LABEL[entry.sourceType]})`}
        </p>
        {entry.sourceReference !== undefined && <p className="text-slate-600">{entry.sourceReference}</p>}
        {href !== undefined && (
          <a href={href} target="_blank" rel="noopener noreferrer" className={`${link} break-all`}>
            {entry.sourceUrl}
          </a>
        )}
        {when}
      </>
    );
  }

  return (
    <>
      <p className="text-slate-900">
        {entry.fromStatus === undefined
          ? `Lead aberto como ${entry.toStatus === undefined ? '—' : STATUS_LABEL[entry.toStatus]}`
          : `${STATUS_LABEL[entry.fromStatus]} → ${entry.toStatus === undefined ? '—' : STATUS_LABEL[entry.toStatus]}`}
      </p>
      {entry.reason !== undefined && <p className="text-slate-600">{entry.reason}</p>}
      {when}
    </>
  );
}
