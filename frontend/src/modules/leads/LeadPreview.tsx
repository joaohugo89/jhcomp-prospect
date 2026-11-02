import { AlarmClock, ExternalLink, Globe, Mail, Phone, X } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { formatCurrency, formatDateTime, safeHref } from '../../app/format';
import { CHANNEL_LABEL, INTERACTION_TYPE_LABEL, SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { PriorityBadge, ScoreIndicator, StatusBadge } from '../../components/Badges';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary } from '../../components/styles';
import { getCompany } from '../../services/companyService';
import { getLeadTimeline } from '../../services/leadService';
import { getLeadSummary } from '../../services/worklistService';
import type { TimelineEntry } from '../../types/lead';
import type { WorklistItem } from '../../types/worklist';
import { daysLabel, STALE_DAYS } from '../crm/pipelineSignals';

const DAY = 86_400_000;
const RECENT = 3;

/**
 * Resumo do lead ao lado da lista: o essencial para decidir sem sair dela.
 * Painel não modal (a lista continua usável); Esc fecha e devolve o foco a
 * quem abriu.
 */
export function LeadPreview({
  leadId,
  now,
  onClose,
}: {
  readonly leadId: string;
  readonly now: number;
  readonly onClose: () => void;
}): React.JSX.Element {
  const [summary, reload] = useLoad(leadId, (signal) => getLeadSummary(leadId, signal));
  const heading = useRef<HTMLHeadingElement>(null);
  const opener = useRef<Element | null>(null);

  // Ao abrir, o foco vai para o título; ao fechar, volta para quem abriu.
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

  return (
    <>
      {/* Celular e tablet: o resumo cobre a lista, e tocar fora dele fecha, sem acionar a
          linha que está embaixo. No desktop não há fundo: clicar em outra linha troca o resumo. */}
      {/* oxlint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
      <div
        aria-hidden="true"
        data-testid="fundo-resumo"
        className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
        onClick={onClose}
      />
      <aside
        id="previa-lead"
        aria-label="Resumo do lead"
        // No celular sobra uma faixa à esquerda: é onde se toca para fechar.
        className="fixed inset-y-0 right-0 z-40 flex w-[calc(100%-3rem)] max-w-md flex-col border-l border-slate-200 bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Resumo do lead</p>
          <button
            type="button"
            aria-label="Fechar resumo"
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={onClose}
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {summary.kind !== 'ready' ? (
            <LoadStateView state={summary} onRetry={reload} notFoundMessage="Lead não encontrado." />
          ) : (
            <PreviewBody item={summary.data} now={now} headingRef={heading} />
          )}
        </div>
        {summary.kind === 'ready' && (
          <div className="flex gap-2 border-t border-slate-100 px-5 py-3">
            <Link to={`/leads/${summary.data.leadId}`} className={`${buttonPrimary} flex-1`}>
              Abrir lead
            </Link>
            <Link to={`/companies/${summary.data.companyId}`} className={buttonSecondary}>
              Ver empresa
            </Link>
          </div>
        )}
      </aside>
    </>
  );
}

function PreviewBody({
  item,
  now,
  headingRef,
}: {
  readonly item: WorklistItem;
  readonly now: number;
  readonly headingRef: React.RefObject<HTMLHeadingElement | null>;
}): React.JSX.Element {
  const days =
    item.stageEnteredAt === undefined
      ? undefined
      : Math.max(0, Math.floor((now - Date.parse(item.stageEnteredAt)) / DAY));
  const overdue = item.nextTaskDueAt !== undefined && Date.parse(item.nextTaskDueAt) < now;

  return (
    <>
      <h2 ref={headingRef} tabIndex={-1} className="text-lg font-bold text-slate-900 focus:outline-none">
        {item.companyName}
      </h2>
      <p className="text-sm text-slate-500">
        {[item.segment ?? 'Sem segmento', `${item.city}/${item.state}`].join(' · ')}
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <StatusBadge status={item.status} />
        <PriorityBadge priority={item.priority} />
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-3">
        <Fact
          label="Responsável"
          value={item.assigneeName ?? 'Sem responsável'}
          muted={item.assigneeName === undefined}
        />
        <Fact
          label="Valor"
          value={item.estimatedValue === undefined ? 'Sem valor' : formatCurrency(item.estimatedValue)}
          muted={item.estimatedValue === undefined}
        />
        <Fact
          label="Na etapa"
          value={days === undefined ? '—' : days === 0 ? 'desde hoje' : daysLabel(days)}
          tone={days !== undefined && days >= STALE_DAYS ? 'text-amber-800' : undefined}
        />
        <Fact
          label="Score"
          value={item.score === undefined ? 'Sem análise' : <ScoreIndicator score={item.score} />}
          muted={item.score === undefined}
        />
        <div className="col-span-2 rounded-xl bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200/70">
          <dt className="text-[11px] text-slate-500">Próxima tarefa</dt>
          <dd className="mt-0.5 text-sm text-slate-900">
            {item.nextTaskTitle === undefined ? (
              <span className="text-slate-500">Nenhuma tarefa aberta</span>
            ) : (
              <>
                <span className="font-medium">{item.nextTaskTitle}</span>
                {item.nextTaskDueAt !== undefined && (
                  <span
                    className={`mt-0.5 flex items-center gap-1 text-xs ${overdue ? 'font-semibold text-red-700' : 'text-slate-500'}`}
                  >
                    {overdue && <AlarmClock aria-hidden="true" className="size-3" />}
                    {`${overdue ? 'Atrasada · ' : 'Até '}${formatDateTime(item.nextTaskDueAt)}`}
                  </span>
                )}
              </>
            )}
          </dd>
        </div>
      </dl>

      <CompanyContact companyId={item.companyId} />
      <RecentActivity leadId={item.leadId} />
    </>
  );
}

function Fact({
  label,
  value,
  muted = false,
  tone,
}: {
  readonly label: string;
  readonly value: React.ReactNode;
  readonly muted?: boolean;
  readonly tone?: string | undefined;
}): React.JSX.Element {
  return (
    <div className="rounded-xl bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200/70">
      <dt className="text-[11px] text-slate-500">{label}</dt>
      <dd
        className={`mt-0.5 truncate text-sm font-semibold ${muted ? 'font-normal text-slate-400' : (tone ?? 'text-slate-900')}`}
      >
        {value}
      </dd>
    </div>
  );
}

/** Contato comercial da empresa. Abrir telefone ou e-mail não é envio (regra 5). */
function CompanyContact({ companyId }: { readonly companyId: string }): React.JSX.Element | null {
  const [company] = useLoad(companyId, (signal) => getCompany(companyId, signal));
  if (company.kind !== 'ready') {
    return null;
  }
  const { phone, businessEmail, websiteUrl } = company.data;
  const site = safeHref(websiteUrl);
  if (phone === undefined && businessEmail === undefined && site === undefined) {
    return <p className="mt-5 text-xs text-slate-500">Empresa sem telefone, e-mail ou site cadastrado.</p>;
  }
  return (
    <section aria-labelledby="previa-contato" className="mt-5">
      <h3 id="previa-contato" className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">
        Contato da empresa
      </h3>
      <ul className="mt-2 space-y-1.5 text-sm">
        {phone !== undefined && (
          <ContactLine
            icon={<Phone className="size-3.5" />}
            href={`tel:${phone.replace(/[^\d+]/g, '')}`}
            text={phone}
          />
        )}
        {businessEmail !== undefined && (
          <ContactLine icon={<Mail className="size-3.5" />} href={`mailto:${businessEmail}`} text={businessEmail} />
        )}
        {site !== undefined && websiteUrl !== undefined && (
          <ContactLine icon={<Globe className="size-3.5" />} href={site} text={websiteUrl} external />
        )}
      </ul>
    </section>
  );
}

function ContactLine({
  icon,
  href,
  text,
  external = false,
}: {
  readonly icon: React.ReactNode;
  readonly href: string;
  readonly text: string;
  readonly external?: boolean;
}): React.JSX.Element {
  return (
    <li className="flex min-w-0 items-center gap-2 text-slate-700">
      <span aria-hidden="true" className="text-slate-400">
        {icon}
      </span>
      <a
        href={href}
        className="truncate hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
        {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}
      >
        {text}
      </a>
      {external && <ExternalLink aria-hidden="true" className="size-3 shrink-0 text-slate-400" />}
    </li>
  );
}

function RecentActivity({ leadId }: { readonly leadId: string }): React.JSX.Element | null {
  const [timeline] = useLoad(leadId, (signal) => getLeadTimeline(leadId, 0, signal));
  if (timeline.kind !== 'ready') {
    return null;
  }
  const entries = timeline.data.content.slice(0, RECENT);
  return (
    <section aria-labelledby="previa-historico" className="mt-5">
      <h3 id="previa-historico" className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">
        Últimas atividades
      </h3>
      {entries.length === 0 ? (
        <p className="mt-2 text-xs text-slate-500">Sem atividade registrada.</p>
      ) : (
        <ol className="mt-2 space-y-2">
          {entries.map((entry) => (
            <li key={entry.id} className="text-sm">
              <p className="text-slate-800">{describe(entry)}</p>
              <p className="text-xs text-slate-400">
                {`${formatDateTime(entry.occurredAt)}${entry.actorName === undefined ? '' : ` · ${entry.actorName}`}`}
              </p>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function describe(entry: TimelineEntry): string {
  if (entry.type === 'INTERACTION') {
    const kind = entry.interactionType === undefined ? 'Interação' : INTERACTION_TYPE_LABEL[entry.interactionType];
    return entry.channel === undefined ? kind : `${kind} por ${CHANNEL_LABEL[entry.channel]}`;
  }
  if (entry.type === 'SOURCE') {
    return `Origem: ${entry.sourceName ?? '—'}${entry.sourceType === undefined ? '' : ` (${SOURCE_TYPE_LABEL[entry.sourceType]})`}`;
  }
  const to = entry.toStatus === undefined ? '—' : STATUS_LABEL[entry.toStatus];
  return entry.fromStatus === undefined ? `Lead aberto como ${to}` : `${STATUS_LABEL[entry.fromStatus]} → ${to}`;
}
