import { card } from '../../../components/styles';

interface DashboardSectionProps {
  /** Id do título; nomeia a região para leitores de tela. */
  readonly id: string;
  readonly title: string;
  /** Rótulo pequeno acima do título (ex.: "Desempenho no período"). */
  readonly eyebrow?: string;
  readonly description?: React.ReactNode;
  /** Selo ou controle à direita do título; quebra para baixo em tela estreita. */
  readonly actions?: React.ReactNode;
  readonly className?: string;
  readonly children: React.ReactNode;
}

/** Cartão com título. Todas as seções do dashboard usam o mesmo cartão e o mesmo respiro. */
export function DashboardSection({
  id,
  title,
  eyebrow,
  description,
  actions,
  className = '',
  children,
}: DashboardSectionProps): React.JSX.Element {
  return (
    <section aria-labelledby={id} className={`${card} p-4 sm:p-5 ${className}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          {eyebrow !== undefined && (
            <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">{eyebrow}</p>
          )}
          <h2 id={id} className={`text-sm font-semibold text-slate-900 ${eyebrow === undefined ? '' : 'mt-0.5'}`}>
            {title}
          </h2>
          {description !== undefined && <div className="mt-0.5 text-xs text-slate-500">{description}</div>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  );
}
