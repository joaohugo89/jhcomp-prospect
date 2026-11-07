import { useEffect, useEffectEvent, useState } from 'react';

import { localDate } from '../../app/format';
import { useLastReady, useLoad } from '../../app/useLoad';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import {
  getDashboardActivity,
  getDashboardAttention,
  getDashboardConversion,
  getDashboardFunnel,
  getDashboardSummary,
  getDashboardTimeline,
  type DashboardFilters,
} from '../../services/crmService';
import { getTeam } from '../../services/teamService';
import type { Session } from '../../types/session';
import { AttentionPanel } from './components/AttentionPanel';
import { ConversionFunnel } from './components/ConversionFunnel';
import { ConversionGauge } from './components/ConversionGauge';
import { ConversionReport } from './components/ConversionReport';
import { DashboardHeader, DashboardLayout } from './components/DashboardLayout';
import { DataQualityNotice } from './components/DataQualityNotice';
import { Freshness } from './components/Freshness';
import { HeaderFilters, PRESETS } from './components/HeaderFilters';
import { KeyMetrics } from './components/KeyMetrics';
import { LeadSourceSplit } from './components/LeadSourceSplit';
import { PerformanceHero, type GranularityChoice } from './components/PerformanceHero';
import { RecentActivityList } from './components/RecentActivityList';
import { StatusProgressList } from './components/StatusProgressList';
import {
  previousPeriod,
  toAttention,
  toActivity,
  toConversion,
  toDashboardData,
  toFunnel,
  toTimeSeries,
} from './model/dashboardData';

/** A short feed, the height of the funnel beside it; the rest is on later pages. */
const ACTIVITY_PAGE_SIZE = 6;

/** Daily columns become too thin to read beyond this. */
const MAX_DAILY_DAYS = 92;

/** Returning to the tab after this reloads the numbers on its own. */
const STALE_AFTER_MS = 2 * 60_000;

/**
 * Dashboard (RF-070, RF-071, doc 08 §4).
 *
 * This page is the only place that talks to the API: it holds the filters,
 * fetches the data and converts it into the view model
 * (`model/dashboardData`). The components in `components/` only present what
 * they are given.
 */
export function DashboardPage({ session }: { readonly session: Session }): React.JSX.Element {
  // The draft edited in the panel; `filters` is what is applied.
  const [draft, setDraft] = useState(defaultFilters);
  const [filters, setFilters] = useState(draft);
  const [granularity, setGranularity] = useState<GranularityChoice>('AUTO');
  const key = [
    filters.from,
    filters.to,
    filters.segment.trim(),
    filters.city.trim(),
    filters.source,
    filters.assignee,
  ].join('|');

  // Same length, immediately before: the base for the change shown in the hero.
  const anterior = previousPeriod({ from: filters.from, to: filters.to });
  const previousFilters = { ...filters, from: anterior.from, to: anterior.to };

  const [summary, reloadSummary] = useLoad(key, async (signal) => ({
    response: await getDashboardSummary(filters, signal),
    receivedAt: Date.now(),
  }));
  const [previous, reloadPrevious] = useLoad(`prev|${key}`, (signal) => getDashboardSummary(previousFilters, signal));
  const [conversion, reloadConversion] = useLoad(key, (signal) => getDashboardConversion(filters, signal));
  const [attention, reloadAttention] = useLoad(key, (signal) => getDashboardAttention(filters, signal));
  const [funnel, reloadFunnel] = useLoad(key, (signal) => getDashboardFunnel(filters, signal));
  const [team] = useLoad('team', (signal) => getTeam(signal));

  const dailyAllowed = days(filters) <= MAX_DAILY_DAYS;
  const unit = granularity === 'AUTO' || (granularity === 'DAY' && !dailyAllowed) ? undefined : granularity;
  const [timeline, reloadTimeline] = useLoad(`${key}|${unit ?? ''}`, (signal) =>
    getDashboardTimeline(filters, unit, signal),
  );
  // While reloading, the previous chart stays, dimmed: no layout jump.
  const lastTimeline = useLastReady(timeline);
  const serie = lastTimeline === null ? null : toTimeSeries(lastTimeline);

  // When the summary arrived: drives "updated at" and the "N days ago"
  // reference. It comes from the load itself, outside rendering, which has to
  // stay pure.
  const loadedAt = summary.kind === 'ready' ? summary.data.receivedAt : null;
  const [refreshRequestedAt, setRefreshRequestedAt] = useState(0);
  const [activityToken, setActivityToken] = useState(0);
  const refreshing = loadedAt === null || refreshRequestedAt > loadedAt;

  function refresh(): void {
    setRefreshRequestedAt(Date.now());
    setActivityToken((token) => token + 1);
    reloadSummary();
    reloadPrevious();
    reloadConversion();
    reloadAttention();
    reloadFunnel();
    reloadTimeline();
  }

  const onVisible = useEffectEvent(() => {
    if (document.visibilityState === 'visible' && loadedAt !== null && Date.now() - loadedAt > STALE_AFTER_MS) {
      refresh();
    }
  });
  useEffect(() => {
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, []);

  const filtersControl = (
    <div className="flex flex-col items-end gap-1">
      <Filters
        filters={filters}
        draft={draft}
        onDraftChange={setDraft}
        onFilters={setFilters}
        team={team.kind === 'ready' ? team.data.content : []}
        currentUserId={session.userId}
      />
      <Freshness loadedAt={loadedAt} refreshing={refreshing} onRefresh={refresh} />
    </div>
  );

  if (summary.kind !== 'ready') {
    return (
      <div className="space-y-4">
        <DashboardHeader filters={filtersControl} />
        <LoadStateView state={summary} onRetry={reloadSummary} />
      </div>
    );
  }

  const data = toDashboardData(summary.data.response);
  const previousData = previous.kind === 'ready' ? toDashboardData(previous.data).periodMetrics : undefined;
  const conversionView = conversion.kind === 'ready' ? toConversion(conversion.data) : undefined;
  const now = loadedAt ?? 0;
  const leadsHref = (status: string): string => leadsUrl(filters, { status });
  const sourceHref = (source: string): string => leadsUrl(filters, { source });

  return (
    <DashboardLayout
      subtitle={`Visão geral da prospecção · ${shortDate(data.period.from)} a ${shortDate(data.period.to)}`}
      filters={filtersControl}
      attention={
        attention.kind === 'ready' ? (
          <AttentionPanel atencao={toAttention(attention.data)} now={now} />
        ) : (
          <LoadStateView state={attention} onRetry={reloadAttention} />
        )
      }
      hero={
        <PerformanceHero
          atual={data.periodMetrics}
          anterior={previousData}
          serie={serie}
          recarregando={timeline.kind === 'loading' && serie !== null}
          leadsNoPeriodo={conversionView?.total.leads}
          granularity={granularity}
          diarioPermitido={dailyAllowed}
          onGranularidade={setGranularity}
          fallback={<LoadStateView state={timeline} onRetry={reloadTimeline} />}
        />
      }
      highlights={
        <>
          <ConversionGauge conversion={conversionView} />
          <LeadSourceSplit conversion={conversionView} sourceHref={sourceHref} />
          <DataQualityNotice semSegmento={data.companiesWithoutSegment} />
        </>
      }
      metrics={<KeyMetrics period={data.periodMetrics} current={data.currentMetrics} leadsHref={leadsHref} />}
      funnel={
        <>
          <StatusProgressList items={data.leadsByStatus} filtered={isFiltered(filters)} leadsHref={leadsHref} />
          {funnel.kind === 'ready' ? (
            <ConversionFunnel funil={toFunnel(funnel.data)} leadsHref={leadsHref} />
          ) : (
            <LoadStateView state={funnel} onRetry={reloadFunnel} />
          )}
        </>
      }
      activity={
        <div className="space-y-3">
          {/* Trocar os filtros recria a lista (key), voltando à primeira página. */}
          <RecentActivityPanel key={key} filters={filters} token={activityToken} />
          {conversionView !== undefined && <ConversionReport conversion={conversionView} />}
        </div>
      }
    />
  );
}

interface FiltersProps {
  readonly filters: DashboardFilters;
  readonly draft: DashboardFilters;
  readonly onDraftChange: (next: DashboardFilters) => void;
  readonly onFilters: (next: DashboardFilters) => void;
  readonly team: React.ComponentProps<typeof HeaderFilters>['team'];
  readonly currentUserId: string;
}

function Filters({ filters, draft, onDraftChange, onFilters, team, currentUserId }: FiltersProps): React.JSX.Element {
  return (
    <HeaderFilters
      draft={draft}
      onDraftChange={onDraftChange}
      onApply={() => {
        onFilters(draft);
      }}
      onPreset={(count) => {
        const next = { ...draft, ...lastDays(count) };
        onDraftChange(next);
        onFilters(next);
      }}
      activePreset={presetOf(filters)}
      team={team}
      currentUserId={currentUserId}
    />
  );
}

/**
 * Loads the paginated activity and hands `RecentActivityList` the converted
 * result.
 *
 * When the page changes, the current page stays on screen, dimmed, until the
 * new one arrives (`useLastReady`): the scroll position does not jump and
 * focus stays on the button that was clicked. `token` changes when the
 * operator asks to refresh the dashboard.
 */
function RecentActivityPanel({
  filters,
  token,
}: {
  readonly filters: DashboardFilters;
  readonly token: number;
}): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${page}|${token}`, (signal) =>
    getDashboardActivity(filters, page, ACTIVITY_PAGE_SIZE, signal),
  );
  const shown = useLastReady(state);
  const loadingNext = state.kind === 'loading' && shown !== null;

  return (
    <section aria-labelledby="atividade" aria-busy={loadingNext} className="space-y-3">
      <h2 id="atividade" className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase">
        Interações e atividades
      </h2>
      {shown === null || (state.kind !== 'ready' && state.kind !== 'loading') ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : (
        <>
          <div className={`transition-opacity ${loadingNext ? 'opacity-60' : ''}`}>
            <RecentActivityList atividades={shown.content.map(toActivity)} />
          </div>
          {shown.content.length > 0 && <Pagination page={shown} onChange={setPage} />}
        </>
      )}
    </section>
  );
}

/**
 * The lead list with the dashboard's filters (drill-down). The period does
 * not travel with it: the list shows who is in the stage now, and has no date
 * filter.
 */
function leadsUrl(filters: DashboardFilters, extra: Readonly<Record<string, string>>): string {
  const params = new URLSearchParams();
  const base: Readonly<Record<string, string>> = {
    segment: filters.segment.trim(),
    city: filters.city.trim(),
    source: filters.source,
    ...extra,
  };
  for (const [name, value] of Object.entries(base)) {
    if (value !== '') {
      params.set(name, value);
    }
  }
  const query = params.toString();
  return query === '' ? '/leads' : `/leads?${query}`;
}

/** The last `count` days, counting today. */
function lastDays(count: number): Pick<DashboardFilters, 'from' | 'to'> {
  const today = new Date();
  const start = new Date(today);
  start.setDate(today.getDate() - (count - 1));
  return { from: localDate(start), to: localDate(today) };
}

/** 30 days is the server's default (D25). */
function defaultFilters(): DashboardFilters {
  return { ...lastDays(30), segment: '', city: '', source: '', assignee: '' };
}

/** The current funnel ignores the period; only the other filters can empty it. */
function isFiltered(filters: DashboardFilters): boolean {
  return (
    filters.segment.trim() !== '' || filters.city.trim() !== '' || filters.source !== '' || filters.assignee !== ''
  );
}

/** The shortcut whose period matches the applied one, so it can be marked active. */
function presetOf(filters: DashboardFilters): number | undefined {
  return PRESETS.find((count) => {
    const range = lastDays(count);
    return range.from === filters.from && range.to === filters.to;
  });
}

/** Days in the period, counting both ends. */
function days(filters: DashboardFilters): number {
  const start = Date.parse(`${filters.from}T00:00:00Z`);
  const end = Date.parse(`${filters.to}T00:00:00Z`);
  return Number.isNaN(start) || Number.isNaN(end) ? 0 : Math.round((end - start) / 86_400_000) + 1;
}

/** YYYY-MM-DD as DD/MM, without going through a time zone: it is a calendar date. */
function shortDate(value: string): string {
  const [, month, day] = value.split('-');
  return month === undefined || day === undefined ? value : `${day}/${month}`;
}
