import { useState } from 'react';

import { formatCurrency, formatDateTime, localDate } from '../../app/format';
import { CHANNEL_LABEL, INTERACTION_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { buttonSecondary, input, link } from '../../components/styles';
import { getDashboardActivity, getDashboardSummary, type DashboardFilters } from '../../services/crmService';
import type { Activity, DashboardSummary } from '../../types/crm';
import { LEAD_STATUSES } from '../../types/lead';

/** Últimos 30 dias, contando hoje, como o padrão do servidor (D25). */
function defaultPeriod(): DashboardFilters {
  const today = new Date();
  const start = new Date(today);
  start.setDate(today.getDate() - 29);
  return { from: localDate(start), to: localDate(today) };
}

/** Indicadores comerciais e atividade recente (RF-070, RF-071, doc 08 §4). */
export function DashboardPage(): React.JSX.Element {
  const [draft, setDraft] = useState(defaultPeriod);
  const [period, setPeriod] = useState(draft);
  const key = `${period.from}:${period.to}`;
  const [summary, reloadSummary] = useLoad(key, (signal) => getDashboardSummary(period, signal));
  const invalid = draft.from === '' || draft.to === '' || draft.from > draft.to;

  return (
    <>
      <PageHeader title="Dashboard" description="Visão geral da prospecção." />

      <form
        aria-label="Período"
        className="mb-6 flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (!invalid) {
            setPeriod(draft);
          }
        }}
      >
        <label className="text-sm font-medium text-slate-700">
          De
          <input
            type="date"
            className={input}
            value={draft.from}
            required
            onChange={(event) => {
              setDraft({ ...draft, from: event.target.value });
            }}
          />
        </label>
        <label className="text-sm font-medium text-slate-700">
          Até
          <input
            type="date"
            className={input}
            value={draft.to}
            required
            onChange={(event) => {
              setDraft({ ...draft, to: event.target.value });
            }}
          />
        </label>
        <button type="submit" className={buttonSecondary} disabled={invalid}>
          Aplicar
        </button>
        {draft.from > draft.to && draft.to !== '' && (
          <p role="alert" className="text-sm text-red-700">
            A data inicial deve ser anterior à final.
          </p>
        )}
      </form>

      {summary.kind !== 'ready' ? (
        <LoadStateView state={summary} onRetry={reloadSummary} />
      ) : (
        <Summary data={summary.data} />
      )}

      <ActivityList key={key} period={period} />
    </>
  );
}

function Summary({ data }: { readonly data: DashboardSummary }): React.JSX.Element {
  const { summary } = data;
  const periodTiles: readonly [string, string][] = [
    ['Empresas adicionadas', String(summary.companiesAdded)],
    ['Contatos registrados', String(summary.contactsRegistered)],
    ['Negócios ganhos', String(summary.dealsWon)],
    ['Valor ganho', formatCurrency(summary.wonValue)],
  ];
  const currentTiles: readonly [string, string][] = [
    ['Leads ativos', String(summary.activeLeads)],
    ['Propostas em aberto', String(summary.openProposals)],
    ['Tarefas abertas', String(summary.openTasks)],
    ['Tarefas atrasadas', String(summary.overdueTasks)],
  ];
  const statusCounts = LEAD_STATUSES.map((status) => [status, summary.leadsByStatus[status] ?? 0] as const);
  const totalLeads = statusCounts.reduce((sum, [, count]) => sum + count, 0);

  return (
    <div className="space-y-6">
      <Tiles title={`No período (${formatDay(data.from)} a ${formatDay(data.to)})`} tiles={periodTiles} />
      <Tiles title="Agora" tiles={currentTiles} />

      <section aria-labelledby="por-status" className="rounded-lg border border-slate-200 bg-white p-6">
        <h2 id="por-status" className="text-base font-semibold">
          Leads por status
        </h2>
        {totalLeads === 0 ? (
          <p className="mt-2 text-sm text-slate-600">
            {'Nenhum lead ainda. '}
            <Link to="/companies" className={link}>
              Cadastre ou importe empresas
            </Link>
            {' para começar.'}
          </p>
        ) : (
          <dl className="mt-3 space-y-2 text-sm">
            {statusCounts.map(([status, count]) => (
              <div key={status} className="grid grid-cols-[10rem_3rem_1fr] items-center gap-2">
                <dt className="text-slate-700">{STATUS_LABEL[status]}</dt>
                <dd className="text-right font-mono tabular-nums">{count}</dd>
                <dd aria-hidden="true" className="h-2 rounded bg-slate-100">
                  <div className="h-2 rounded bg-brand-500" style={{ width: `${(count / totalLeads) * 100}%` }} />
                </dd>
              </div>
            ))}
          </dl>
        )}
      </section>
    </div>
  );
}

function Tiles({
  title,
  tiles,
}: {
  readonly title: string;
  readonly tiles: readonly [string, string][];
}): React.JSX.Element {
  return (
    <section aria-label={title}>
      <h2 className="mb-2 text-sm font-semibold text-slate-700">{title}</h2>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {tiles.map(([label, value]) => (
          <div key={label} className="rounded-lg border border-slate-200 bg-white p-4">
            <dt className="text-xs text-slate-600">{label}</dt>
            <dd className="mt-1 font-mono text-2xl font-semibold tabular-nums text-slate-900">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function ActivityList({ period }: { readonly period: DashboardFilters }): React.JSX.Element {
  // Trocar o período recria este componente (key), voltando à primeira página.
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${period.from}:${period.to}:${page}`, (signal) =>
    getDashboardActivity(period, page, signal),
  );

  return (
    <section aria-labelledby="atividade" className="mt-6 rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="atividade" className="text-base font-semibold">
        Atividade recente
      </h2>
      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.content.length === 0 ? (
        <p className="mt-2 text-sm text-slate-600">Nenhuma atividade no período.</p>
      ) : (
        <>
          <ul className="mt-3 divide-y divide-slate-100">
            {state.data.content.map((item) => (
              <li key={`${item.type}-${item.leadId}-${item.occurredAt}`} className="py-2 text-sm">
                <Link to={`/leads/${item.leadId}`} className={link}>
                  {item.companyName}
                </Link>
                <p className="text-slate-700">{describe(item)}</p>
                <p className="text-xs text-slate-500">
                  <time dateTime={item.occurredAt}>{formatDateTime(item.occurredAt)}</time>
                  {item.actorName === undefined ? '' : ` · ${item.actorName}`}
                </p>
              </li>
            ))}
          </ul>
          <Pagination page={state.data} onChange={setPage} />
        </>
      )}
    </section>
  );
}

function describe(item: Activity): string {
  if (item.type === 'INTERACTION') {
    const kind = item.interactionType === undefined ? 'Interação' : INTERACTION_TYPE_LABEL[item.interactionType];
    return item.channel === undefined ? kind : `${kind} · ${CHANNEL_LABEL[item.channel]}`;
  }
  const to = item.toStatus === undefined ? '—' : STATUS_LABEL[item.toStatus];
  return item.fromStatus === undefined ? `Lead aberto como ${to}` : `${STATUS_LABEL[item.fromStatus]} → ${to}`;
}

/** AAAA-MM-DD como DD/MM/AAAA, sem passar por fuso: é data de calendário. */
function formatDay(value: string): string {
  const [year, month, day] = value.split('-');
  return year === undefined || month === undefined || day === undefined ? value : `${day}/${month}/${year}`;
}
