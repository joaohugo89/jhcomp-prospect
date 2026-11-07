import { AlarmClock } from 'lucide-react';
import { useEffect, useRef } from 'react';

import { formatCurrency, formatDateTime } from '../../app/format';
import { CHANNEL_LABEL, INTERACTION_TYPE_LABEL, SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { PriorityBadge, ScoreIndicator, StatusBadge } from '../../components/Badges';
import { PanelFact, SidePanel } from '../../components/SidePanel';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary } from '../../components/styles';
import { getLeadTimeline } from '../../services/leadService';
import { getLeadSummary } from '../../services/worklistService';
import type { TimelineEntry } from '../../types/lead';
import type { WorklistItem } from '../../types/worklist';
import { CompanyContact } from '../companies/CompanyContact';
import { daysLabel, STALE_DAYS } from '../crm/pipelineSignals';

const DAY = 86_400_000;
const RECENT = 3;

/**
 * The lead summary beside the list: enough to decide without leaving it. A
 * non-modal panel — the list stays usable — and Esc closes it and returns
 * focus to whatever opened it.
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
  // On load focus moves to the title; on close the panel returns it to the opener.
  useEffect(() => {
    if (summary.kind === 'ready') {
      heading.current?.focus();
    }
  }, [summary.kind]);

  return (
    <SidePanel
      id="previa-lead"
      label="Resumo do lead"
      backdropTestId="fundo-resumo"
      onClose={onClose}
      {...(summary.kind === 'ready'
        ? {
            footer: (
              <>
                <Link to={`/leads/${summary.data.leadId}`} className={`${buttonPrimary} flex-1`}>
                  Abrir lead
                </Link>
                <Link to={`/companies/${summary.data.companyId}`} className={buttonSecondary}>
                  Ver empresa
                </Link>
              </>
            ),
          }
        : {})}
    >
      {summary.kind !== 'ready' ? (
        <LoadStateView state={summary} onRetry={reload} notFoundMessage="Lead não encontrado." />
      ) : (
        <PreviewBody item={summary.data} now={now} headingRef={heading} />
      )}
    </SidePanel>
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
      <h2 ref={headingRef} tabIndex={-1} className="text-lg font-bold text-stone-900 focus:outline-none">
        {item.companyName}
      </h2>
      <p className="text-sm text-stone-500">
        {[item.segment ?? 'Sem segmento', `${item.city}/${item.state}`].join(' · ')}
      </p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <StatusBadge status={item.status} />
        <PriorityBadge priority={item.priority} />
      </div>

      <dl className="mt-5 grid grid-cols-2 gap-3">
        <PanelFact label="Responsável" muted={item.assigneeName === undefined}>
          {item.assigneeName ?? 'Sem responsável'}
        </PanelFact>
        <PanelFact label="Valor" muted={item.estimatedValue === undefined}>
          {item.estimatedValue === undefined ? 'Sem valor' : formatCurrency(item.estimatedValue)}
        </PanelFact>
        <PanelFact label="Na etapa" tone={days !== undefined && days >= STALE_DAYS ? 'text-amber-800' : undefined}>
          {days === undefined ? '—' : days === 0 ? 'desde hoje' : daysLabel(days)}
        </PanelFact>
        <PanelFact label="Score" muted={item.score === undefined}>
          {item.score === undefined ? 'Sem análise' : <ScoreIndicator score={item.score} />}
        </PanelFact>
        <div className="col-span-2 rounded-xl bg-stone-50 px-3 py-2.5 ring-1 ring-stone-200/70">
          <dt className="text-[11px] text-stone-500">Próxima tarefa</dt>
          <dd className="mt-0.5 text-sm text-stone-900">
            {item.nextTaskTitle === undefined ? (
              <span className="text-stone-500">Nenhuma tarefa aberta</span>
            ) : (
              <>
                <span className="font-medium">{item.nextTaskTitle}</span>
                {item.nextTaskDueAt !== undefined && (
                  <span
                    className={`mt-0.5 flex items-center gap-1 text-xs ${overdue ? 'font-semibold text-red-700' : 'text-stone-500'}`}
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

function RecentActivity({ leadId }: { readonly leadId: string }): React.JSX.Element | null {
  const [timeline] = useLoad(leadId, (signal) => getLeadTimeline(leadId, 0, signal));
  if (timeline.kind !== 'ready') {
    return null;
  }
  const entries = timeline.data.content.slice(0, RECENT);
  return (
    <section aria-labelledby="previa-historico" className="mt-5">
      <h3 id="previa-historico" className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase">
        Últimas atividades
      </h3>
      {entries.length === 0 ? (
        <p className="mt-2 text-xs text-stone-500">Sem atividade registrada.</p>
      ) : (
        <ol className="mt-2 space-y-2">
          {entries.map((entry) => (
            <li key={entry.id} className="text-sm">
              <p className="text-stone-800">{describe(entry)}</p>
              <p className="text-xs text-stone-400">
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
