import { Link } from '../../../app/router';
import type { Conversao } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

/**
 * Cor fixa por origem, nunca pela posição na lista: trocar o filtro não
 * repinta as origens que continuam. A legenda (nome ao lado) carrega a
 * identidade; a cor só reforça.
 */
const SOURCE_COLOR: Readonly<Record<string, string>> = {
  CSV_IMPORT: 'bg-brand-600',
  MANUAL: 'bg-emerald-600',
  INTEGRATION: 'bg-violet-500',
};
const OTHER_COLOR = 'bg-slate-400';

/** Origem dos leads abertos no período: participação de cada uma no total. */
export function LeadSourceSplit({
  conversao,
  sourceHref,
}: {
  readonly conversao: Conversao | undefined;
  /** Drill-down: a origem abre a lista de leads dela; ausente na pré-visualização. */
  readonly sourceHref?: (source: string) => string;
}): React.JSX.Element {
  const total = conversao?.total.leads ?? 0;

  return (
    <DashboardSection
      id="origem-leads"
      title="Origem dos leads"
      description="Leads abertos no período"
      actions={
        conversao === undefined ? undefined : (
          <span className="text-xs font-semibold text-slate-500 tabular-nums">{`${total} no total`}</span>
        )
      }
    >
      {conversao === undefined ? (
        <p className="mt-4 text-xs text-slate-500">Carregando…</p>
      ) : total === 0 ? (
        <p className="mt-4 text-xs text-slate-500">Nenhum lead aberto no período.</p>
      ) : (
        <ul className="mt-4 space-y-3.5">
          {conversao.porOrigem.map((grupo) => {
            const share = grupo.leads / total;
            return (
              <li key={grupo.chave ?? 'sem-origem'}>
                <div className="flex items-center justify-between gap-2 text-xs">
                  <span className="flex min-w-0 items-center gap-2 font-medium text-slate-700">
                    <span
                      aria-hidden="true"
                      className={`size-2 shrink-0 rounded-full ${SOURCE_COLOR[grupo.chave ?? ''] ?? OTHER_COLOR}`}
                    />
                    {sourceHref === undefined || grupo.chave === undefined ? (
                      <span className="truncate">{grupo.nome}</span>
                    ) : (
                      <Link to={sourceHref(grupo.chave)} className="truncate hover:text-brand-700 hover:underline">
                        {grupo.nome}
                      </Link>
                    )}
                  </span>
                  <span className="shrink-0 font-semibold text-slate-900 tabular-nums">
                    {grupo.leads}
                    <span className="ml-1 font-normal text-slate-400">{`(${Math.round(share * 100)}%)`}</span>
                  </span>
                </div>
                <div aria-hidden="true" className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className={`h-full rounded-full ${SOURCE_COLOR[grupo.chave ?? ''] ?? OTHER_COLOR}`}
                    style={{ width: `${share * 100}%` }}
                  />
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {conversao !== undefined && total > 0 && (
        <p className="mt-4 border-t border-slate-100 pt-3 text-xs text-slate-500">
          {`${conversao.total.contatados} ${conversao.total.contatados === 1 ? 'contatado' : 'contatados'} · ${conversao.total.ganhos} ${conversao.total.ganhos === 1 ? 'ganho' : 'ganhos'}`}
        </p>
      )}
    </DashboardSection>
  );
}
