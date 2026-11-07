import { card } from './styles';

/**
 * One number in an overview (inside a `<dl>`): a small label and a large
 * value. With no value yet, while loading, it shows a dash.
 */
export function StatTile({
  label,
  value,
  icon,
  tone = 'text-stone-900',
  children,
}: {
  readonly label: string;
  readonly value: number | string | undefined;
  readonly icon?: React.ReactNode;
  /** Colour of the number when it needs attention (outstanding items, say). */
  readonly tone?: string;
  /** Extra content over the tile, such as a button covering it to filter. */
  readonly children?: React.ReactNode;
}): React.JSX.Element {
  return (
    <div className={`${card} relative min-w-0 px-3 py-2.5 sm:px-4 sm:py-3`}>
      <dt className="flex items-center gap-1.5 truncate text-[11px] font-medium text-stone-500 sm:text-xs">
        {icon !== undefined && <span aria-hidden="true">{icon}</span>}
        {label}
      </dt>
      <dd className={`mt-0.5 font-display text-xl font-semibold tabular-nums sm:mt-1 sm:text-2xl ${tone}`}>
        {value ?? '—'}
      </dd>
      {children}
    </div>
  );
}
