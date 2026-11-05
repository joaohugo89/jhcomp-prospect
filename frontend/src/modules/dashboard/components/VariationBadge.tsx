import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react';

import type { Variacao } from '../model/dashboardData';

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1, signDisplay: 'exceptZero' });

/**
 * Variação contra o período anterior de mesmo tamanho. Direção por ícone e
 * sinal, não só por cor. Sem base de comparação, diz isso em vez de um "+∞%".
 */
export function VariationBadge({ variacao }: { readonly variacao: Variacao }): React.JSX.Element | null {
  if (variacao.tipo === 'semDados') {
    return null;
  }
  if (variacao.tipo === 'semBase') {
    return <span className="text-xs font-medium text-stone-500">sem registro no período anterior</span>;
  }
  const { valor } = variacao;
  const Icon = valor > 0 ? ArrowUpRight : valor < 0 ? ArrowDownRight : Minus;
  const tone =
    valor > 0
      ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
      : valor < 0
        ? 'bg-red-50 text-red-700 ring-red-200'
        : 'bg-stone-100 text-stone-600 ring-stone-200';
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-stone-500">
      <span className={`inline-flex items-center gap-0.5 rounded-md px-1.5 py-0.5 font-semibold ring-1 ${tone}`}>
        <Icon aria-hidden="true" className="size-3.5" />
        {`${percent.format(valor)}%`}
      </span>
      vs. período anterior
    </span>
  );
}
