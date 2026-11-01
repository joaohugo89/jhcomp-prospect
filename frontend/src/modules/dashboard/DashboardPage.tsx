import { useState } from 'react';

import { localDate } from '../../app/format';
import { useLastReady, useLoad } from '../../app/useLoad';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import {
  getDashboardActivity,
  getDashboardConversion,
  getDashboardSummary,
  getDashboardTimeline,
  type DashboardFilters,
} from '../../services/crmService';
import { ConversionGauge } from './components/ConversionGauge';
import { ConversionReport } from './components/ConversionReport';
import { DashboardHeader, DashboardLayout } from './components/DashboardLayout';
import { HeaderFilters, PRESETS } from './components/HeaderFilters';
import { KeyMetrics } from './components/KeyMetrics';
import { LeadSourceSplit } from './components/LeadSourceSplit';
import { PerformanceHero, type GranularityChoice } from './components/PerformanceHero';
import { RecentActivityList } from './components/RecentActivityList';
import { StatusProgressList } from './components/StatusProgressList';
import {
  periodoAnterior,
  toAtividade,
  toConversao,
  toDashboardData,
  toSerie,
  type DashboardData,
} from './model/dashboardData';

/** Feed curto, da altura do funil ao lado; o resto fica nas páginas seguintes. */
const ACTIVITY_PAGE_SIZE = 6;

/** Por dia, colunas finas demais deixam de ser legíveis. */
const MAX_DAILY_DAYS = 92;

/**
 * Dashboard (RF-070, RF-071, doc 08 §4).
 *
 * Esta página é o único ponto que fala com a API: guarda os filtros, busca os
 * dados e os converte para o modelo de tela (`model/dashboardData`). Os
 * componentes em `components/` só apresentam o que recebem.
 */
export function DashboardPage(): React.JSX.Element {
  // Rascunho editado no painel; `filters` é o que está aplicado.
  const [draft, setDraft] = useState(defaultFilters);
  const [filters, setFilters] = useState(draft);
  const [granularity, setGranularity] = useState<GranularityChoice>('AUTO');
  const key = [filters.from, filters.to, filters.segment.trim(), filters.city.trim(), filters.source].join('|');

  // Mesmo tamanho, imediatamente antes: base da variação mostrada no destaque.
  const anterior = periodoAnterior({ de: filters.from, ate: filters.to });
  const previousFilters = { ...filters, from: anterior.de, to: anterior.ate };

  const [summary, reloadSummary] = useLoad(key, (signal) => getDashboardSummary(filters, signal));
  const [previous] = useLoad(`prev|${key}`, (signal) => getDashboardSummary(previousFilters, signal));
  const [conversion] = useLoad(key, (signal) => getDashboardConversion(filters, signal));

  const dailyAllowed = days(filters) <= MAX_DAILY_DAYS;
  const unit = granularity === 'AUTO' || (granularity === 'DAY' && !dailyAllowed) ? undefined : granularity;
  const [timeline, reloadTimeline] = useLoad(`${key}|${unit ?? ''}`, (signal) =>
    getDashboardTimeline(filters, unit, signal),
  );
  // Ao recarregar, o gráfico anterior fica, atenuado: sem salto de layout.
  const lastTimeline = useLastReady(timeline);
  const serie = lastTimeline === null ? null : toSerie(lastTimeline);

  if (summary.kind !== 'ready') {
    return (
      <DashboardShell filters={filters} draft={draft} onDraftChange={setDraft} onFilters={setFilters}>
        <LoadStateView state={summary} onRetry={reloadSummary} />
      </DashboardShell>
    );
  }

  const data: DashboardData = toDashboardData(summary.data);
  const previousData = previous.kind === 'ready' ? toDashboardData(previous.data).metricasPeriodo : undefined;
  const conversao = conversion.kind === 'ready' ? toConversao(conversion.data) : undefined;

  return (
    <DashboardLayout
      subtitle={`Visão geral da prospecção · ${dataCurta(data.periodo.de)} a ${dataCurta(data.periodo.ate)}`}
      filters={<Filters filters={filters} draft={draft} onDraftChange={setDraft} onFilters={setFilters} />}
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
          <LeadSourceSplit conversao={conversao} />
        </>
      }
      metrics={<KeyMetrics periodo={data.metricasPeriodo} atuais={data.metricasAtuais} />}
      funnel={<StatusProgressList itens={data.leadsPorStatus} filtrado={isFiltered(filters)} />}
      activity={
        <div className="space-y-3">
          {/* Trocar os filtros recria a lista (key), voltando à primeira página. */}
          <RecentActivityPanel key={key} filters={filters} />
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
}

function Filters({ filters, draft, onDraftChange, onFilters }: FiltersProps): React.JSX.Element {
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
    />
  );
}

/** Cabeçalho com filtros enquanto o resumo carrega ou falha: o operador ainda pode trocar o período. */
function DashboardShell({
  children,
  ...props
}: FiltersProps & { readonly children: React.ReactNode }): React.JSX.Element {
  return (
    <div className="space-y-6">
      <DashboardHeader filters={<Filters {...props} />} />
      {children}
    </div>
  );
}

/**
 * Carrega a atividade paginada e entrega a `RecentActivityList` já convertida.
 *
 * Ao trocar de página, a página atual continua na tela, atenuada, até a nova
 * chegar (`useLastReady`): a rolagem não pula e o foco fica no botão clicado.
 */
function RecentActivityPanel({ filters }: { readonly filters: DashboardFilters }): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${page}`, (signal) =>
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

/** Últimos `count` dias, contando hoje. */
function lastDays(count: number): Pick<DashboardFilters, 'from' | 'to'> {
  const today = new Date();
  const start = new Date(today);
  start.setDate(today.getDate() - (count - 1));
  return { from: localDate(start), to: localDate(today) };
}

/** 30 dias é o padrão do servidor (D25). */
function defaultFilters(): DashboardFilters {
  return { ...lastDays(30), segment: '', city: '', source: '' };
}

/** O funil ignora o período; só os outros filtros o esvaziam. */
function isFiltered(filters: DashboardFilters): boolean {
  return filters.segment.trim() !== '' || filters.city.trim() !== '' || filters.source !== '';
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
