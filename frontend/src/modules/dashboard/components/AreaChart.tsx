import { useState } from 'react';

/** One point of the series: a short `label` on the axis, the full `title` in the tooltip. */
export interface ChartPoint {
  readonly key: string;
  readonly label: string;
  readonly title: string;
  readonly value: number;
}

interface AreaChartProps {
  readonly name: string;
  readonly points: readonly ChartPoint[];
  readonly format: (value: number) => string;
  /** Compact label for the Y axis (for example "R$ 2 mil"). */
  readonly formatTick: (value: number) => string;
  /** Counts step by whole numbers on the axis; monetary values do not. */
  readonly integer: boolean;
}

const TICKS = 4;
const MAX_X_LABELS = 6;
/** Headroom above the largest value, as a % of the height, so the peak marker fits inside the chart. */
const HEADROOM = 0.18;

/**
 * A single-series area chart (one series needs no legend: the title names
 * it). The line and the area are SVG that stretches with the width; points,
 * labels and the tooltip are HTML on top, so they do not distort. The vertical
 * line follows the pointer. By keyboard it is a `slider`: the arrows step
 * through the intervals and the screen reader announces interval and value.
 */
export function AreaChart({ name, points, format, formatTick, integer }: AreaChartProps): React.JSX.Element {
  const [active, setActive] = useState<number | null>(null);
  const max = Math.max(0, ...points.map((point) => point.value));
  const top = niceTop(max, integer);
  const scaleTop = top / (1 - HEADROOM);
  const count = points.length;
  const xOf = (index: number): number => ((index + 0.5) / count) * 100;
  const yOf = (value: number): number => 100 - (value / scaleTop) * 100;
  const peak = max > 0 ? points.findIndex((point) => point.value === max) : -1;
  const current = active === null ? undefined : points[active];
  const announced = current ?? points[count - 1];
  const step = Math.ceil(count / MAX_X_LABELS);

  const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'} ${xOf(index)} ${yOf(point.value)}`).join(' ');
  const area = count === 0 ? '' : `${line} L ${xOf(count - 1)} 100 L ${xOf(0)} 100 Z`;

  return (
    <div className="grid grid-cols-[auto_1fr] gap-x-3">
      <div aria-hidden="true" className="relative h-44 w-10 sm:h-48">
        {ticks(top).map((tick) => (
          <span
            key={tick}
            className="absolute right-0 translate-y-1/2 text-[11px] whitespace-nowrap text-stone-400 tabular-nums"
            style={{ bottom: `${100 - yOf(tick)}%` }}
          >
            {formatTick(tick)}
          </span>
        ))}
      </div>

      <div
        role="slider"
        tabIndex={0}
        aria-label={`${name}. Use as setas para percorrer os intervalos.`}
        aria-valuemin={1}
        aria-valuemax={Math.max(1, count)}
        aria-valuenow={(active ?? count - 1) + 1}
        aria-valuetext={announced === undefined ? '' : `${announced.title}: ${format(announced.value)}`}
        className="relative h-44 rounded-sm focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-brand-500 sm:h-48"
        onMouseLeave={() => {
          setActive(null);
        }}
        onBlur={() => {
          setActive(null);
        }}
        onKeyDown={(event) => {
          const keys: Readonly<Record<string, number>> = {
            ArrowLeft: (active ?? count) - 1,
            ArrowRight: (active ?? -1) + 1,
            Home: 0,
            End: count - 1,
          };
          const next = keys[event.key];
          if (next !== undefined) {
            event.preventDefault();
            setActive(Math.min(count - 1, Math.max(0, next)));
          }
        }}
      >
        {/* Grade: linhas finas e contínuas, recessivas; a base um tone acima. */}
        {ticks(top).map((tick) => (
          <span
            key={tick}
            aria-hidden="true"
            className={`absolute inset-x-0 h-px ${tick === 0 ? 'bg-stone-200' : 'bg-stone-100'}`}
            style={{ top: `${yOf(tick)}%` }}
          />
        ))}

        <svg
          aria-hidden="true"
          className="absolute inset-0 size-full overflow-visible"
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
        >
          <path d={area} className="fill-brand-700/10" />
          <path
            d={line}
            fill="none"
            className="stroke-brand-700"
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          />
        </svg>

        {active !== null && (
          <span
            aria-hidden="true"
            className="absolute inset-y-0 w-px -translate-x-1/2 bg-stone-300"
            style={{ left: `${xOf(active)}%` }}
          />
        )}

        {/* Marcadores só no pico, no último ponto e no ponto ativo: um em cada ponto vira ruído. */}
        {points.map((point, index) =>
          index === peak || index === count - 1 || index === active ? (
            <span
              key={point.key}
              aria-hidden="true"
              className={`absolute size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-chart-gap ${
                index === active ? 'bg-brand-700' : 'bg-brand-600'
              }`}
              style={{ left: `${xOf(index)}%`, top: `${yOf(point.value)}%` }}
            />
          ) : null,
        )}

        {peak >= 0 && active === null && (
          <span
            aria-hidden="true"
            className="absolute rounded-md bg-brand-50 px-1.5 py-0.5 text-[11px] font-semibold whitespace-nowrap text-brand-700 ring-1 ring-brand-100"
            style={{ top: `calc(${yOf(max)}% - 1.75rem)`, ...alongX(xOf(peak)) }}
          >
            {`Pico · ${points[peak]?.label ?? ''}`}
          </span>
        )}

        <div aria-hidden="true" className="absolute inset-0 flex">
          {points.map((point, index) => (
            <div
              key={point.key}
              className="h-full flex-1"
              onMouseEnter={() => {
                setActive(index);
              }}
            />
          ))}
        </div>

        {current !== undefined && active !== null && (
          <div
            aria-hidden="true"
            className="pointer-events-none absolute top-0 z-10 rounded-xl border border-stone-200 glass-panel px-3 py-2 whitespace-nowrap shadow-md"
            style={alongX(xOf(active))}
          >
            <p className="text-sm font-semibold text-stone-900 tabular-nums">{format(current.value)}</p>
            <p className="text-xs text-stone-500">{current.title}</p>
          </div>
        )}
      </div>

      <div />
      <div aria-hidden="true" className="relative mt-2 h-4">
        {points.map((point, index) =>
          index % step === 0 ? (
            <span
              key={point.key}
              className="absolute text-[11px] whitespace-nowrap text-stone-400"
              style={alongX(xOf(index))}
            >
              {point.label}
            </span>
          ) : null,
        )}
      </div>
    </div>
  );
}

/** A "round" ceiling for the axis, divided into four equal steps. */
function niceTop(max: number, integer: boolean): number {
  if (max <= 0) {
    return TICKS;
  }
  const raw = max / TICKS;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const factor = [1, 2, 2.5, 5, 10].find((candidate) => candidate * magnitude >= raw) ?? 10;
  const stepValue = factor * magnitude;
  return (integer ? Math.max(1, Math.ceil(stepValue)) : stepValue) * TICKS;
}

function ticks(top: number): readonly number[] {
  return Array.from({ length: TICKS + 1 }, (_, index) => (top / TICKS) * index);
}

/**
 * An element centred at `x` (%), pinned to the edge near the extremes, so the
 * tooltip, the peak marker and the label never leave the chart on a narrow
 * screen. `translate` — not `transform` — is the property Tailwind 4 classes
 * use.
 */
function alongX(x: number): React.CSSProperties {
  if (x < 12) {
    return { left: 0 };
  }
  if (x > 88) {
    return { right: 0 };
  }
  return { left: `${x}%`, translate: '-50% 0' };
}
