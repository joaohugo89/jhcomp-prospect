import { card } from '../../../components/styles';

interface DashboardSectionProps {
  /** The title's id; it names the region for screen readers. */
  readonly id: string;
  readonly title: string;
  /** A small label above the title (for example "Desempenho no período"). */
  readonly eyebrow?: string;
  readonly description?: React.ReactNode;
  /** A badge or control to the right of the title; wraps below on a narrow screen. */
  readonly actions?: React.ReactNode;
  readonly className?: string;
  readonly children: React.ReactNode;
}

/** A titled card. Every dashboard section uses the same card and the same spacing. */
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
            <p className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase">{eyebrow}</p>
          )}
          <h2 id={id} className={`text-sm font-semibold text-stone-900 ${eyebrow === undefined ? '' : 'mt-0.5'}`}>
            {title}
          </h2>
          {description !== undefined && <div className="mt-0.5 text-xs text-stone-500">{description}</div>}
        </div>
        {actions}
      </div>
      {children}
    </section>
  );
}
