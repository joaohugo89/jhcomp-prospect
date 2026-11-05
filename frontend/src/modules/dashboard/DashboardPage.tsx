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
  periodoAnterior,
  toAtencao,
  toAtividade,
  toConversao,
  toDashboardData,
  toFunil,
  toSerie,
} from './model/dashboardData';

/** Feed curto, da altura do funil ao lado; o resto fica nas páginas seguintes. */
const ACTIVITY_PAGE_SIZE = 6;

/** Por dia, colunas finas demais deixam de ser legíveis. */
const MAX_DAILY_DAYS = 92;

/** Ao voltar para a aba depois disso, os números são recarregados sozinhos. */
const STALE_AFTER_MS = 2 * 60_000;

/**
 * Dashboard (RF-070, RF-071, doc 08 §4).
 *
 * Esta página é o único ponto que fala com a API: guarda os filtros, busca os
 * dados e os converte para o modelo de tela (`model/dashboardData`). Os
 * componentes em `components/` só apresentam o que recebem.
 */
export function DashboardPage({ session }: { readonly session: Session }): React.JSX.Element {
  // Rascunho editado no painel; `filters` é o que está aplicado.
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

  // Mesmo tamanho, imediatamente antes: base da variação mostrada no destaque.
  const anterior = periodoAnterior({ de: filters.from, ate: filters.to });
  const previousFilters = { ...filters, from: anterior.de, to: anterior.ate };

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
  // Ao recarregar, o gráfico anterior fica, atenuado: sem salto de layout.
  const lastTimeline = useLastReady(timeline);
  const serie = lastTimeline === null ? null : toSerie(lastTimeline);

  // Quando o resumo chegou: "Atualizado às" e referência para "há N dias". Vem do
  // próprio carregamento (fora da renderização, que precisa ser pura).
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
  const previousData = previous.kind === 'ready' ? toDashboardData(previous.data).metricasPeriodo : undefined;
  const conversao = conversion.kind === 'ready' ? toConversao(conversion.data) : undefined;
  const now = loadedAt ?? 0;
  const leadsHref = (status: string): string => leadsUrl(filters, { status });
  const sourceHref = (source: string): string => leadsUrl(filters, { source });

  return (
    <DashboardLayout
      subtitle={`Visão geral da prospecção · ${dataCurta(data.periodo.de)} a ${dataCurta(data.periodo.ate)}`}
      filters={filtersControl}
      attention={
        attention.kind === 'ready' ? (
          <AttentionPanel atencao={toAtencao(attention.data)} now={now} />
        ) : (
          <LoadStateView state={attention} onRetry={reloadAttention} />
        )
      }
      hero={
        <PerformanceHero
          atual={data.metricasPeriodo}
          anterior={previousData}
          serie={serie}
          recarregando={timeline.kind === 'loading' && serie !== null}
          leadsNoPeriodo={conversao?.total.leads}
          granularidade={granularity}
          diarioPermitido={dailyAllowed}
          onGranularidade={setGranularity}
          fallback={<LoadStateView state={timeline} onRetry={reloadTimeline} />}
        />
      }
      highlights={
        <>
          <ConversionGauge conversao={conversao} />
          <LeadSourceSplit conversao={conversao} sourceHref={sourceHref} />
          <DataQualityNotice semSegmento={data.empresasSemSegmento} />
        </>
      }
      metrics={<KeyMetrics periodo={data.metricasPeriodo} atuais={data.metricasAtuais} leadsHref={leadsHref} />}
      funnel={
        <>
          <StatusProgressList itens={data.leadsPorStatus} filtrado={isFiltered(filters)} leadsHref={leadsHref} />
          {funnel.kind === 'ready' ? (
            <ConversionFunnel funil={toFunil(funnel.data)} leadsHref={leadsHref} />
          ) : (
            <LoadStateView state={funnel} onRetry={reloadFunnel} />
          )}
        </>
      }
      activity={
        <div className="space-y-3">
          {/* Trocar os filtros recria a lista (key), voltando à primeira página. */}
          <RecentActivityPanel key={key} filters={filters} token={activityToken} />
          {conversao !== undefined && <ConversionReport conversao={conversao} />}
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
 * Carrega a atividade paginada e entrega a `RecentActivityList` já convertida.
 *
 * Ao trocar de página, a página atual continua na tela, atenuada, até a nova
 * chegar (`useLastReady`): a rolagem não pula e o foco fica no botão clicado.
 * `token` muda quando o operador pede para atualizar o dashboard.
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
      <h2 id="atividade" className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">
        Interações e atividades
      </h2>
      {shown === null || (state.kind !== 'ready' && state.kind !== 'loading') ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : (
        <>
          <div className={`transition-opacity ${loadingNext ? 'opacity-60' : ''}`}>
            <RecentActivityList atividades={shown.content.map(toAtividade)} />
          </div>
          {shown.content.length > 0 && <Pagination page={shown} onChange={setPage} />}
        </>
      )}
    </section>
  );
}

/**
 * Lista de leads com os filtros do dashboard (drill-down). O período não vai:
 * a lista mostra quem está na etapa agora, e não tem filtro de data.
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

/** Últimos `count` dias, contando hoje. */
function lastDays(count: number): Pick<DashboardFilters, 'from' | 'to'> {
  const today = new Date();
  const start = new Date(today);
  start.setDate(today.getDate() - (count - 1));
  return { from: localDate(start), to: localDate(today) };
}

/** 30 dias é o padrão do servidor (D25). */
function defaultFilters(): DashboardFilters {
  return { ...lastDays(30), segment: '', city: '', source: '', assignee: '' };
}

/** O funil de agora ignora o período; só os outros filtros o esvaziam. */
function isFiltered(filters: DashboardFilters): boolean {
  return (
    filters.segment.trim() !== '' || filters.city.trim() !== '' || filters.source !== '' || filters.assignee !== ''
  );
}

/** Atalho cujo período coincide com o aplicado, para marcá-lo como ativo. */
function presetOf(filters: DashboardFilters): number | undefined {
  return PRESETS.find((count) => {
    const range = lastDays(count);
    return range.from === filters.from && range.to === filters.to;
  });
}

/** Dias do período, contando as duas pontas. */
function days(filters: DashboardFilters): number {
  const start = Date.parse(`${filters.from}T00:00:00Z`);
  const end = Date.parse(`${filters.to}T00:00:00Z`);
  return Number.isNaN(start) || Number.isNaN(end) ? 0 : Math.round((end - start) / 86_400_000) + 1;
}

/** AAAA-MM-DD como DD/MM, sem passar por fuso: é data de calendário. */
function dataCurta(valor: string): string {
  const [, mes, dia] = valor.split('-');
  return mes === undefined || dia === undefined ? valor : `${dia}/${mes}`;
}
