import { ChevronsLeftRight, ChevronsRightLeft } from 'lucide-react';

import { formatCurrency } from '../../app/format';
import { Link } from '../../app/router';
import { link } from '../../components/styles';
import type { BoardCard, BoardColumn } from '../../types/crm';
import { PipelineCard, type Density, type Moving } from './PipelineCard';

interface PipelineColumnProps {
  readonly column: BoardColumn;
  readonly density: Density;
  readonly now: number;
  /** Collapsed: a narrow strip with name and count that still accepts cards. */
  readonly collapsed: boolean;
  readonly onToggle: () => void;
  readonly dragging: BoardCard | null;
  readonly moving: Moving | null;
  readonly onDragStart: (card: BoardCard) => void;
  readonly onDragEnd: () => void;
  readonly onDrop: (card: BoardCard) => void;
  readonly onMove: (moving: Moving | null) => void;
  readonly onMoved: () => void;
}

const toggleButton =
  'rounded-md p-1 text-stone-400 hover:glass hover:text-stone-700 focus-visible:outline-2 focus-visible:outline-brand-500';

/** One Kanban stage: a header with count and value, and cards that scroll inside. */
export function PipelineColumn(props: PipelineColumnProps): React.JSX.Element {
  const { column, collapsed, dragging } = props;
  const { stage, total, totalValue, recentDays, cards } = column;
  const accepts = dragging !== null && dragging.allowedTransitions.includes(stage.key);
  // While dragging, stages that cannot take the card are dimmed, so the possible target stands out.
  const refuses = dragging !== null && !accepts && dragging.status !== stage.key;
  const headingId = `coluna-${stage.key}`;

  const tone = accepts
    ? 'border-brand-500 bg-brand-50'
    : `border-stone-200 bg-stone-100 ${refuses ? 'opacity-50' : ''}`;

  return (
    // Dragging is a mouse shortcut; the keyboard uses each card's move button.
    // oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <section
      aria-labelledby={headingId}
      className={`flex shrink-0 flex-col rounded-xl border transition-opacity ${tone} ${
        collapsed ? 'w-12 items-center px-1 py-2' : 'w-72 p-2 md:max-h-full md:w-auto md:min-w-48 md:flex-1 md:basis-0'
      }`}
      onDragOver={(event) => {
        if (accepts) {
          event.preventDefault();
        }
      }}
      onDrop={(event) => {
        event.preventDefault();
        if (dragging !== null && accepts) {
          props.onDrop(dragging);
        }
      }}
    >
      {collapsed ? (
        <>
          <button
            type="button"
            aria-expanded={false}
            aria-label={`Expandir ${stage.name}`}
            className={toggleButton}
            onClick={props.onToggle}
          >
            <ChevronsLeftRight aria-hidden="true" className="size-4" />
          </button>
          <h2
            id={headingId}
            className="mt-2 text-xs font-semibold whitespace-nowrap text-stone-700 [writing-mode:vertical-rl]"
          >
            {`${stage.name} · ${total}`}
          </h2>
        </>
      ) : (
        <>
          <div className="mb-2 flex items-start justify-between gap-2">
            <div className="min-w-0">
              <h2 id={headingId} className="flex items-baseline gap-2 text-sm font-semibold text-stone-800">
                <span className="truncate">{stage.name}</span>
                <span
                  className="rounded-full glass px-1.5 text-xs font-semibold text-stone-600 tabular-nums ring-1 ring-stone-200"
                  aria-label={`${total} leads`}
                >
                  {total}
                </span>
              </h2>
              <p className="mt-0.5 text-xs text-stone-500">
                {[
                  totalValue > 0 ? formatCurrency(totalValue) : undefined,
                  recentDays === undefined ? undefined : `últimos ${recentDays} dias`,
                ]
                  .filter((part) => part !== undefined)
                  .join(' · ') || ' '}
              </p>
            </div>
            <button
              type="button"
              aria-expanded
              aria-label={`Recolher ${stage.name}`}
              className={toggleButton}
              onClick={props.onToggle}
            >
              <ChevronsRightLeft aria-hidden="true" className="size-4" />
            </button>
          </div>

          {cards.length === 0 ? (
            <p className="py-4 text-center text-xs text-stone-500">Nenhum lead nesta etapa.</p>
          ) : (
            // Scrolls inside the column; the -mx/px keeps the cards' focus ring visible.
            <ul className="space-y-2 md:-mx-1 md:min-h-0 md:flex-1 md:overflow-y-auto md:px-1 md:pb-1">
              {cards.map((card) => (
                <li key={card.leadId}>
                  <PipelineCard
                    card={card}
                    density={props.density}
                    now={props.now}
                    moving={props.moving?.leadId === card.leadId ? props.moving : null}
                    onDragStart={() => {
                      props.onDragStart(card);
                    }}
                    onDragEnd={props.onDragEnd}
                    onMove={props.onMove}
                    onMoved={props.onMoved}
                  />
                </li>
              ))}
            </ul>
          )}

          {(total > cards.length || recentDays !== undefined) && (
            <p className="mt-2 text-xs text-stone-600">
              {total > cards.length ? `Mostrando ${cards.length} de ${total}. ` : ''}
              {recentDays === undefined ? 'Use os filtros ou a ' : 'Os mais antigos estão na '}
              <Link to="/leads" className={link}>
                lista de leads
              </Link>
              .
            </p>
          )}
        </>
      )}
    </section>
  );
}
