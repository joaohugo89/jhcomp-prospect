import { Link } from '../../../app/router';
import type { ConversionView } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

/**
 * A fixed colour per origin, never by position in the list: changing the
 * filter does not repaint the origins that remain. The legend beside it
 * carries the identity; colour only reinforces.
 */
const SOURCE_COLOR: Readonly<Record<string, string>> = {
  CSV_IMPORT: 'bg-brand-600',
  MANUAL: 'bg-emerald-600',
  INTEGRATION: 'bg-violet-500',
};
const OTHER_COLOR = 'bg-stone-400';

/** Origin of the leads opened in the period: each one's share of the total. */
export function LeadSourceSplit({
  conversion,
  sourceHref,
}: {
  readonly conversion: ConversionView | undefined;
  /** Drill-down: the origin opens its own list of leads; absent in the preview. */
  readonly sourceHref?: (source: string) => string;
}): React.JSX.Element {
  const total = conversion?.total.leads ?? 0;

  return (
    <DashboardSection
      id="origem-leads"
      title="Origem dos leads"
      description="Leads abertos no período"
      actions={
        conversion === undefined ? undefined : (
          <span className="text-xs font-semibold text-stone-500 tabular-nums">{`${total} no total`}</span>
        )
      }
    >
      {conversion === undefined ? (
        <p className="mt-4 text-xs text-stone-500">Carregando…</p>
      ) : total === 0 ? (
        <p className="mt-4 text-xs text-stone-500">Nenhum lead aberto no período.</p>
      ) : (
        <ul className="mt-4 space-y-3.5">
          {conversion.bySource.map((grupo) => {
            const share = grupo.leads / total;
            return (
              <li key={grupo.key ?? 'sem-origem'}>
                <div className="flex items-center justify-between gap-2 text-xs">
                  <span className="flex min-w-0 items-center gap-2 font-medium text-stone-700">
                    <span
                      aria-hidden="true"
                      className={`size-2 shrink-0 rounded-full ${SOURCE_COLOR[grupo.key ?? ''] ?? OTHER_COLOR}`}
                    />
                    {sourceHref === undefined || grupo.key === undefined ? (
                      <span className="truncate">{grupo.label}</span>
                    ) : (
                      <Link to={sourceHref(grupo.key)} className="truncate hover:text-brand-700 hover:underline">
                        {grupo.label}
                      </Link>
                    )}
                  </span>
                  <span className="shrink-0 font-semibold text-stone-900 tabular-nums">
                    {grupo.leads}
                    <span className="ml-1 font-normal text-stone-400">{`(${Math.round(share * 100)}%)`}</span>
                  </span>
                </div>
                <div aria-hidden="true" className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-stone-100">
                  <div
                    className={`h-full rounded-full ${SOURCE_COLOR[grupo.key ?? ''] ?? OTHER_COLOR}`}
                    style={{ width: `${share * 100}%` }}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {conversion !== undefined && total > 0 && (
        <p className="mt-4 border-t border-stone-100 pt-3 text-xs text-stone-500">
          {`${conversion.total.contacted} ${conversion.total.contacted === 1 ? 'contatado' : 'contacted'} · ${conversion.total.won} ${conversion.total.won === 1 ? 'ganho' : 'won'}`}
        </p>
      )}
    </DashboardSection>
  );
}
