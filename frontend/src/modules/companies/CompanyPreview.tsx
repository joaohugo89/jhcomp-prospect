import { Copy, X } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { formatDateTime } from '../../app/format';
import { SOURCE_TYPE_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { StatusBadge } from '../../components/Badges';
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
  const opener = useRef<Element | null>(null);

  useEffect(() => {
    opener.current = document.activeElement;
    return () => {
      if (opener.current instanceof HTMLElement) {
        opener.current.focus();
      }
    };
  }, []);
  useEffect(() => {
    if (summary.kind === 'ready') {
      heading.current?.focus();
    }
  }, [summary.kind]);
  useEffect(() => {
    function onKey(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  const lead = summary.kind === 'ready' ? summary.data.currentLead : undefined;

  return (
    <>
      {/* oxlint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
      <div
        aria-hidden="true"
        data-testid="fundo-previa-empresa"
        className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
        onClick={onClose}
      />
      <aside
        id="previa-empresa"
        aria-label="Prévia da empresa"
        className="fixed inset-y-0 right-0 z-40 flex w-[calc(100%-3rem)] max-w-md flex-col border-l border-slate-200 bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Prévia da empresa</p>
          <button
            type="button"
            aria-label="Fechar prévia"
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={onClose}
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {summary.kind !== 'ready' ? (
            <LoadStateView state={summary} onRetry={reload} notFoundMessage="Empresa não encontrada." />
          ) : (
            <PreviewBody item={summary.data} headingRef={heading} />
          )}
        </div>
        {summary.kind === 'ready' && (
          <div className="flex gap-2 border-t border-slate-100 px-5 py-3">
            <Link to={`/companies/${summary.data.companyId}`} className={`${buttonPrimary} flex-1`}>
              Abrir empresa
            </Link>
            {lead?.open === true && (
              <Link to={`/leads/${lead.leadId}`} className={buttonSecondary}>
                Ver lead
              </Link>
            )}
          </div>
        )}
      </aside>
    </>
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
        <Fact label="Origem" value={item.origin === undefined ? '—' : SOURCE_TYPE_LABEL[item.origin]} />
        <Fact label="Ciclos comerciais" value={String(item.leadCount)} />
        <Fact
          label="Última atividade"
          value={item.lastActivityAt === undefined ? 'Nenhuma' : formatDateTime(item.lastActivityAt)}
          muted={item.lastActivityAt === undefined}
        />
        <Fact label="Cadastrada" value={formatDateTime(item.createdAt)} />
      </dl>

      <CompanyContact companyId={item.companyId} />
    </>
  );
}

function Fact({
  label,
  value,
  muted = false,
}: {
  readonly label: string;
  readonly value: string;
  readonly muted?: boolean;
}): React.JSX.Element {
  return (
    <div className="rounded-xl bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200/70">
      <dt className="text-[11px] text-slate-500">{label}</dt>
      <dd
        className={`mt-0.5 text-sm font-semibold break-words ${muted ? 'font-normal text-slate-400' : 'text-slate-900'}`}
      >
        {value}
      </dd>
    </div>
  );
}
