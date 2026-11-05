import { Copy } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { formatDateTime } from '../../app/format';
import { SOURCE_TYPE_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { StatusBadge } from '../../components/Badges';
import { PanelFact, SidePanel } from '../../components/SidePanel';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary } from '../../components/styles';
import { getCompanySummary } from '../../services/companyService';
import type { CompanyWorklistItem } from '../../types/company';
import { CompanyContact } from './CompanyContact';

/**
 * Prévia da empresa ao lado da lista, no mesmo padrão do resumo do lead:
 * painel não modal no desktop; no celular cobre a lista e tocar fora fecha.
 * Esc fecha e devolve o foco a quem abriu.
 */
export function CompanyPreview({
  companyId,
  onClose,
}: {
  readonly companyId: string;
  readonly onClose: () => void;
}): React.JSX.Element {
  const [summary, reload] = useLoad(companyId, (signal) => getCompanySummary(companyId, signal));
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (summary.kind === 'ready') {
      heading.current?.focus();
    }
  }, [summary.kind]);
  const lead = summary.kind === 'ready' ? summary.data.currentLead : undefined;

  return (
    <SidePanel
      id="previa-empresa"
      label="Prévia da empresa"
      backdropTestId="fundo-previa-empresa"
      onClose={onClose}
      {...(summary.kind === 'ready'
        ? {
            footer: (
              <>
                <Link to={`/companies/${summary.data.companyId}`} className={`${buttonPrimary} flex-1`}>
                  Abrir empresa
                </Link>
                {lead?.open === true && (
                  <Link to={`/leads/${lead.leadId}`} className={buttonSecondary}>
                    Ver lead
                  </Link>
                )}
              </>
            ),
          }
        : {})}
    >
      {summary.kind !== 'ready' ? (
        <LoadStateView state={summary} onRetry={reload} notFoundMessage="Empresa não encontrada." />
      ) : (
        <PreviewBody item={summary.data} headingRef={heading} />
      )}
    </SidePanel>
  );
}

function PreviewBody({
  item,
  headingRef,
}: {
  readonly item: CompanyWorklistItem;
  readonly headingRef: React.RefObject<HTMLHeadingElement | null>;
}): React.JSX.Element {
  const lead = item.currentLead;
  return (
    <>
      <h2 ref={headingRef} tabIndex={-1} className="text-lg font-bold text-slate-900 focus:outline-none">
        {item.name}
      </h2>
      <p className="text-sm text-slate-500">
        {[item.segment ?? 'Sem segmento', `${item.city}/${item.state}`].join(' · ')}
      </p>
      {item.possibleDuplicate && (
        <p className="mt-3 flex items-center gap-1.5 rounded-xl bg-amber-50 px-3 py-2 text-xs text-amber-900 ring-1 ring-amber-200">
          <Copy aria-hidden="true" className="size-3.5 shrink-0" />
          Há outra empresa ativa com o mesmo telefone, site ou nome nesta cidade. Revise no detalhe.
        </p>
      )}

      <dl className="mt-5 grid grid-cols-2 gap-3">
        <div className="col-span-2 rounded-xl bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200/70">
          <dt className="text-[11px] text-slate-500">Lead atual</dt>
          <dd className="mt-1 text-sm">
            {lead === undefined ? (
              <span className="text-slate-500">Nenhum lead aberto ainda</span>
            ) : (
              <span className="flex flex-wrap items-center gap-2">
                <StatusBadge status={lead.status} />
                <span className="text-slate-600">
                  {lead.open ? (lead.assigneeName ?? 'Sem responsável') : 'ciclo encerrado'}
                </span>
              </span>
            )}
          </dd>
        </div>
        <PanelFact label="Origem">{item.origin === undefined ? '—' : SOURCE_TYPE_LABEL[item.origin]}</PanelFact>
        <PanelFact label="Ciclos comerciais">{String(item.leadCount)}</PanelFact>
        <PanelFact label="Última atividade" muted={item.lastActivityAt === undefined}>
          {item.lastActivityAt === undefined ? 'Nenhuma' : formatDateTime(item.lastActivityAt)}
        </PanelFact>
        <PanelFact label="Cadastrada">{formatDateTime(item.createdAt)}</PanelFact>
      </dl>

      <CompanyContact companyId={item.companyId} />
    </>
  );
}
