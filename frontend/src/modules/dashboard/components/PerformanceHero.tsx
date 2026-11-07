import { useState } from 'react';

import { formatCurrency } from '../../../app/format';
import { input } from '../../../components/styles';
import type { Granularity } from '../../../types/crm';
import type { PeriodMetrics, TimeSeries } from '../model/dashboardData';
import { variation } from '../model/dashboardData';
import { AreaChart, type ChartPoint } from './AreaChart';
import { DashboardSection } from './DashboardSection';
import { VariationBadge } from './VariationBadge';

type MetricKey = keyof PeriodMetrics;

interface MetricOption {
  readonly key: MetricKey;
  readonly label: string;
  readonly currency: boolean;
}

/** The period metrics, with the same definitions as the cards. */
const HERO_METRICS: readonly [MetricOption, ...MetricOption[]] = [
  { key: 'wonValue', label: 'Valor ganho', currency: true },
  { key: 'companiesAdded', label: 'Empresas adicionadas', currency: false },
  { key: 'contactsRegistered', label: 'Contatos registrados', currency: false },
  { key: 'dealsWon', label: 'Negócios ganhos', currency: false },
];

export type GranularityChoice = Granularity | 'AUTO';

const GRANULARITY_LABEL: Readonly<Record<Granularity, string>> = { DAY: 'dia', WEEK: 'semana', MONTH: 'mês' };
const MONTHS = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const compact = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 });
/** Summary under the chart, in short form to fit a phone; the exact value is in the title. */
const compactCurrency = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
});

interface PerformanceHeroProps {
  readonly atual: PeriodMetrics;
  /** The previous period of the same length; absent while loading or on failure. */
  readonly anterior: PeriodMetrics | undefined;
  /** The period's series; `null` until the first load arrives. */
  readonly serie: TimeSeries | null;
  /** The series is being reloaded: the previous chart stays, dimmed. */
  readonly recarregando: boolean;
  readonly leadsNoPeriodo: number | undefined;
  readonly granularity: GranularityChoice;
  readonly diarioPermitido: boolean;
  readonly onGranularidade: (choice: GranularityChoice) => void;
  /** The series' loading or error state, when there is no chart to show. */
  readonly fallback?: React.ReactNode;
}

/** The hero block: the chosen metric over time, with total and change. */
export function PerformanceHero(props: PerformanceHeroProps): React.JSX.Element {
  const { atual, anterior, serie, recarregando, leadsNoPeriodo, fallback } = props;
  const [metricKey, setMetricKey] = useState<MetricKey>('wonValue');
  const metric = HERO_METRICS.find((option) => option.key === metricKey) ?? HERO_METRICS[0];
  const format = metric.currency ? formatCurrency : (value: number) => String(value);
  const ticket = atual.dealsWon === 0 ? undefined : atual.wonValue / atual.dealsWon;

  return (
    <DashboardSection
      id="desempenho"
      eyebrow="Desempenho no período"
      title={metric.label}
      actions={
        leadsNoPeriodo === undefined ? undefined : (
          <span className="rounded-lg bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 ring-1 ring-brand-100">
            {`${leadsNoPeriodo} ${leadsNoPeriodo === 1 ? 'lead aberto' : 'leads abertos'} no período`}
          </span>
        )
      }
    >
      <div className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <p className="font-display text-3xl font-semibold tracking-tight text-stone-900">{format(atual[metric.key])}</p>
        <VariationBadge variation={variation(atual[metric.key], anterior?.[metric.key])} />
      </div>

      <div className="mt-5 flex flex-wrap items-end justify-between gap-3">
        <div role="group" aria-label="Métrica do gráfico" className="flex flex-wrap gap-1.5">
          {HERO_METRICS.map((option) => (
            <button
              key={option.key}
              type="button"
              aria-pressed={option.key === metricKey}
              className={`h-8 rounded-full px-3 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
                option.key === metricKey
                  ? 'bg-brand-600 text-white'
                  : 'bg-stone-100 text-stone-600 hover:bg-stone-200 hover:text-stone-900'
              }`}
              onClick={() => {
                setMetricKey(option.key);
              }}
            >
              {option.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-xs font-medium whitespace-nowrap text-stone-500">
          Agrupar por
          <select
            className={`${input} mt-0 h-8 w-auto py-0 text-xs`}
            value={props.granularity}
            onChange={(event) => {
              props.onGranularidade(toChoice(event.target.value));
            }}
          >
            <option value="AUTO">Automático</option>
            <option value="DAY" disabled={!props.diarioPermitido}>
              Dia
            </option>
            <option value="WEEK">Semana</option>
            <option value="MONTH">Mês</option>
          </select>
        </label>
      </div>

      <div className={`mt-6 transition-opacity ${recarregando ? 'opacity-60' : ''}`}>
        {serie === null ? fallback : <SeriesChart serie={serie} metric={metric} format={format} />}
      </div>

      <dl className="mt-6 grid grid-cols-3 gap-2 sm:gap-3">
        <QuickStat label="Valor ganho" value={compactCurrency.format(atual.wonValue)} />
        <QuickStat label="Negócios ganhos" value={String(atual.dealsWon)} />
        <QuickStat label="Ticket médio" value={ticket === undefined ? '—' : compactCurrency.format(ticket)} />
      </dl>
    </DashboardSection>
  );
}

function SeriesChart({
  serie,
  metric,
  format,
}: {
  readonly serie: TimeSeries;
  readonly metric: MetricOption;
  readonly format: (value: number) => string;
}): React.JSX.Element {
  const unit = GRANULARITY_LABEL[serie.granularity];
  const points: readonly ChartPoint[] = serie.points.map((ponto) => ({
    key: ponto.start,
    label: shortLabel(ponto.start, serie.granularity),
    title: longLabel(ponto.start, serie.granularity),
    value: ponto[metric.key],
  }));

  return (
    <>
      <AreaChart
        name={`${metric.label} por ${unit}`}
        points={points}
        format={format}
        formatTick={(value) => (metric.currency ? `R$ ${compact.format(value)}` : compact.format(value))}
        integer={!metric.currency}
      />
      <details className="mt-3 text-xs">
        <summary className="w-fit cursor-pointer font-semibold text-brand-700 hover:underline">
          {`Ver dados por ${unit} em tabela`}
        </summary>
        <div className="mt-2 max-h-56 overflow-auto rounded-xl border border-stone-200/70">
          <table className="min-w-full text-sm">
            <caption className="sr-only">{`${metric.label} por ${unit}`}</caption>
            <thead className="sticky top-0 bg-stone-50 text-left text-xs text-stone-500">
              <tr>
                <th scope="col" className="px-3 py-2 font-medium capitalize">
                  {unit}
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  {metric.label}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {points.map((point) => (
                <tr key={point.key}>
                  <th scope="row" className="px-3 py-1.5 text-left font-normal text-stone-700">
                    {point.title}
                  </th>
                  <td className="px-3 py-1.5 text-right tabular-nums">{format(point.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </>
  );
}

function QuickStat({ label, value }: { readonly label: string; readonly value: string }): React.JSX.Element {
  return (
    <div className="rounded-xl bg-stone-50 px-2 py-2.5 text-center ring-1 ring-stone-200/70">
      <dt className="text-[11px] text-stone-500">{label}</dt>
      <dd className="mt-0.5 truncate text-sm font-bold text-stone-900">{value}</dd>
    </div>
  );
}

function toChoice(value: string): GranularityChoice {
  return value === 'DAY' || value === 'WEEK' || value === 'MONTH' ? value : 'AUTO';
}

function parts(date: string): readonly [string, string, number] {
  const [year = '', month = '', day = ''] = date.split('-');
  return [day, year, Number(month) - 1];
}

function shortLabel(date: string, granularity: Granularity): string {
  const [day, year, month] = parts(date);
  return granularity === 'MONTH'
    ? `${MONTHS[month] ?? ''}/${year.slice(2)}`
    : `${day}/${String(month + 1).padStart(2, '0')}`;
}

function longLabel(date: string, granularity: Granularity): string {
  const [day, year, month] = parts(date);
  const full = `${day}/${String(month + 1).padStart(2, '0')}/${year}`;
  if (granularity === 'MONTH') {
    return `${MONTHS[month] ?? ''} de ${year}`;
  }
  return granularity === 'WEEK' ? `Semana a partir de ${full}` : full;
}
