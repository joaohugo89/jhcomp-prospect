import { RotateCw } from 'lucide-react';

const TIME = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });

/**
 * Quando os números foram carregados e um botão para atualizar. Sem isso o
 * operador não sabe se olha dado de agora ou de horas atrás.
 */
export function Freshness({
  loadedAt,
  refreshing,
  onRefresh,
}: {
  readonly loadedAt: number | null;
  readonly refreshing: boolean;
  readonly onRefresh: () => void;
}): React.JSX.Element {
  return (
    <div className="flex items-center gap-2 text-xs text-slate-500">
      <span aria-live="polite">{loadedAt === null ? 'Carregando…' : `Atualizado às ${TIME.format(loadedAt)}`}</span>
      <button
        type="button"
        aria-label="Atualizar os números"
        title="Atualizar"
        disabled={refreshing}
        className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500 disabled:opacity-60"
        onClick={onRefresh}
      >
        <RotateCw aria-hidden="true" className={`size-4 ${refreshing ? 'animate-spin' : ''}`} />
      </button>
    </div>
  );
}
