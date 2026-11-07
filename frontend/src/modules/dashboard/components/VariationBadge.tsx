import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';

import type { Variation } from '../model/dashboardData';

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1, signDisplay: 'exceptZero' });

/**
 * Change against the previous period of the same length. Direction by icon
 * and sign, not by colour alone. With no base to compare against it says so,
 * rather than printing "+∞%".
 */
export function VariationBadge({ variation }: { readonly variation: Variation }): React.JSX.Element | null {
  if (variation.kind === 'noData') {
    return null;
  }
  if (variation.kind === 'noBase') {
    return <span className="text-xs font-medium text-stone-500">sem registro no período anterior</span>;
  }
  const { value } = variation;
  const Icon = value > 0 ? ArrowUpRight : value < 0 ? ArrowDownRight : Minus;
  const tone =
    value > 0
      ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
      : value < 0
        ? 'bg-red-50 text-red-700 ring-red-200'
        : 'bg-stone-100 text-stone-600 ring-stone-200';
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-stone-500">
      <span className={`inline-flex items-center gap-0.5 rounded-md px-1.5 py-0.5 font-semibold ring-1 ${tone}`}>
        <Icon aria-hidden="true" className="size-3.5" />
        {`${percent.format(value)}%`}
      </span>
      vs. período anterior
    </span>
  );
}
