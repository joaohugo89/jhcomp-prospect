import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { INTERACTION_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useFilters } from '../../app/useFilters';
import { useLoad } from '../../app/useLoad';
import { PriorityBadge, ScoreIndicator } from '../../components/Badges';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { getBoard, type BoardFilters } from '../../services/crmService';
import { changeLeadStatus } from '../../services/leadService';
import type { BoardCard, BoardColumn } from '../../types/crm';
import type { LeadStatus } from '../../types/lead';
import { IRREVERSIBLE, StatusChangeForm } from '../leads/StatusChangeForm';

const NO_FILTERS: BoardFilters = { search: '', segment: '', city: '' };

/** Card com o formulário de mudança aberto e, se veio de arrastar, o destino. */
interface Moving {
  readonly leadId: string;
  readonly target?: LeadStatus;
}

/**
 * Pipeline em Kanban (RF-040, doc 08 §8).
 *
 * Mover card é mudar o status do lead (D2): o servidor valida a transição.
 * Arrastar é atalho; o botão "Mover" faz o mesmo pelo teclado. Destinos que
 * pedem confirmação (`CONTACTED` e estados finais) nunca são aplicados direto
 * ao soltar: abrem o formulário do card com o destino escolhido.
 */
export function PipelinePage(): React.JSX.Element {
  const { draft, setDraft, applied, apply, isFiltered, queryKey } = useFilters(NO_FILTERS);
  const [state, reload] = useLoad(queryKey, (signal) => getBoard(applied, signal));
  const [moving, setMoving] = useState<Moving | null>(null);
  const [dragging, setDragging] = useState<BoardCard | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function drop(card: BoardCard, target: LeadStatus): Promise<void> {
    if (target === card.status || !card.allowedTransitions.includes(target)) {
      return;
    }
    if (target === 'CONTACTED' || IRREVERSIBLE.has(target)) {
      setMoving({ leadId: card.leadId, target });
      return;
    }
    setError(null);
    try {
      await changeLeadStatus(card.leadId, target, '', false);
      reload();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'Esta mudança não é permitida no estado atual. O quadro foi recarregado.'));
      reload();
    }
  }

  return (
    <>
      <PageHeader title="Pipeline" description="Arraste um card ou use “Mover” para mudar o status do lead." />

      <form
        role="search"
        aria-label="Filtrar pipeline"
        className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 md:grid-cols-[2fr_1fr_1fr_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          apply();
        }}
      >
        <label className="text-sm font-medium text-slate-700">
          Buscar empresa
          <input
            type="search"
            className={input}
            value={draft.search}
            onChange={(event) => {
              setDraft({ ...draft, search: event.target.value });
            }}
          />
        </label>
        <label className="text-sm font-medium text-slate-700">
          Segmento
          <input
            className={input}
            value={draft.segment}
            onChange={(event) => {
              setDraft({ ...draft, segment: event.target.value });
            }}
          />
        </label>
        <label className="text-sm font-medium text-slate-700">
          Cidade
          <input
            className={input}
            value={draft.city}
            onChange={(event) => {
              setDraft({ ...draft, city: event.target.value });
            }}
          />
        </label>
        <button type="submit" className={`${buttonSecondary} self-end`}>
          Filtrar
        </button>
      </form>

      <div aria-live="polite">
        {error !== null && (
          <p role="alert" className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-900">
            {error}
          </p>
        )}
      </div>

      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.every((column) => column.total === 0) ? (
        <EmptyState
          title={isFiltered ? 'Nenhum lead corresponde aos filtros.' : 'Nenhum lead na pipeline.'}
          {...(isFiltered
            ? {}
            : {
                description: 'Abra um lead a partir de uma empresa para que ele apareça aqui.',
                action: (
                  <Link to="/companies" className={buttonPrimary}>
                    Ver empresas
                  </Link>
                ),
              })}
        />
      ) : (
        <div className="-mx-4 overflow-x-auto px-4 pb-4">
          <div className="flex gap-3">
            {state.data.map((column) => (
              <Column
                key={column.stage.key}
                column={column}
                dragging={dragging}
                moving={moving}
                onDragStart={setDragging}
                onDragEnd={() => {
                  setDragging(null);
                }}
                onDrop={(card) => {
                  setDragging(null);
                  void drop(card, column.stage.key);
                }}
                onMove={setMoving}
                onMoved={() => {
                  setMoving(null);
                  reload();
                }}
              />
            ))}
          </div>
        </div>
      )}
    </>
  );
}

interface ColumnProps {
  readonly column: BoardColumn;
  readonly dragging: BoardCard | null;
  readonly moving: Moving | null;
  readonly onDragStart: (card: BoardCard) => void;
  readonly onDragEnd: () => void;
  readonly onDrop: (card: BoardCard) => void;
  readonly onMove: (moving: Moving | null) => void;
  readonly onMoved: () => void;
}

function Column({
  column,
  dragging,
  moving,
  onDragStart,
  onDragEnd,
  onDrop,
  onMove,
  onMoved,
}: ColumnProps): React.JSX.Element {
  const { stage, total, cards } = column;
  const accepts = dragging !== null && dragging.allowedTransitions.includes(stage.key);
  const headingId = `coluna-${stage.key}`;

  return (
    // Arrastar é atalho de mouse; o teclado usa o botão "Mover" de cada card.
    // oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <section
      aria-labelledby={headingId}
      className={`flex w-72 shrink-0 flex-col rounded-lg border p-3 ${
        accepts ? 'border-brand-500 bg-brand-50' : 'border-slate-200 bg-slate-100'
      }`}
      onDragOver={(event) => {
        if (accepts) {
          event.preventDefault();
        }
      }}
      onDrop={(event) => {
        event.preventDefault();
        if (dragging !== null && accepts) {
          onDrop(dragging);
        }
      }}
    >
      <h2 id={headingId} className="mb-2 flex items-baseline justify-between text-sm font-semibold text-slate-800">
        {stage.name}
        <span className="font-mono text-xs font-normal text-slate-600" aria-label={`${total} leads`}>
          {total}
        </span>
      </h2>
      {cards.length === 0 ? (
        <p className="py-4 text-center text-xs text-slate-500">Nenhum lead nesta etapa.</p>
      ) : (
        <ul className="space-y-2">
          {cards.map((card) => (
            <li key={card.leadId}>
              <Card
                card={card}
                moving={moving?.leadId === card.leadId ? moving : null}
                onDragStart={() => {
                  onDragStart(card);
                }}
                onDragEnd={onDragEnd}
                onMove={onMove}
                onMoved={onMoved}
              />
            </li>
          ))}
        </ul>
      )}
      {total > cards.length && (
        <p className="mt-2 text-xs text-slate-600">
          {`Mostrando ${cards.length} de ${total}. Use os filtros ou a `}
          <Link to="/leads" className={link}>
            lista de leads
          </Link>
          .
        </p>
      )}
    </section>
  );
}

interface CardProps {
  readonly card: BoardCard;
  readonly moving: Moving | null;
  readonly onDragStart: () => void;
  readonly onDragEnd: () => void;
  readonly onMove: (moving: Moving | null) => void;
  readonly onMoved: () => void;
}

function Card({ card, moving, onDragStart, onDragEnd, onMove, onMoved }: CardProps): React.JSX.Element {
  const draggable = card.allowedTransitions.length > 0;

  return (
    // oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <article
      draggable={draggable}
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', card.leadId);
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      className={`rounded-md border border-slate-200 bg-white p-3 text-sm shadow-sm ${draggable ? 'cursor-grab' : ''}`}
    >
      <Link to={`/leads/${card.leadId}`} className={`${link} break-words`}>
        {card.companyName}
      </Link>
      <p className="text-xs text-slate-600">
        {[card.segment, `${card.city}/${card.state}`].filter((part) => part !== undefined).join(' · ')}
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <PriorityBadge priority={card.priority} />
        <span className="text-xs text-slate-500">
          Score: <ScoreIndicator score={card.score} />
        </span>
      </div>
      {card.service !== undefined && <p className="mt-1 text-xs text-slate-700">{`Serviço: ${card.service}`}</p>}
      {card.lastInteractionType !== undefined && card.lastInteractionAt !== undefined && (
        <p className="mt-1 text-xs text-slate-600">
          {`Última interação: ${INTERACTION_TYPE_LABEL[card.lastInteractionType]} em ${formatDateTime(card.lastInteractionAt)}`}
        </p>
      )}
      {card.nextTaskTitle !== undefined && (
        <p className="mt-1 text-xs text-slate-600">
          {`Próxima tarefa: ${card.nextTaskTitle}${card.nextTaskDueAt === undefined ? '' : ` até ${formatDateTime(card.nextTaskDueAt)}`}`}
        </p>
      )}

      {draggable &&
        (moving === null ? (
          <button
            type="button"
            className={`${buttonSecondary} mt-2 w-full px-2 py-1`}
            aria-label={`Mover ${card.companyName}`}
            onClick={() => {
              onMove({ leadId: card.leadId });
            }}
          >
            Mover
          </button>
        ) : (
          <div className="mt-3 border-t border-slate-200 pt-3">
            <StatusChangeForm
              key={moving.target ?? ''}
              lead={{
                id: card.leadId,
                status: card.status,
                allowedTransitions: card.allowedTransitions,
              }}
              {...(moving.target === undefined ? {} : { initialTarget: moving.target })}
              onChanged={onMoved}
            />
            <button
              type="button"
              className={`${link} mt-2 text-xs`}
              onClick={() => {
                onMove(null);
              }}
            >
              {`Cancelar (continua em ${STATUS_LABEL[card.status]})`}
            </button>
          </div>
        ))}
    </article>
  );
}
