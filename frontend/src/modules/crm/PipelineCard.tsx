import { AlarmClock, ArrowRightLeft, Clock, MessageSquareOff } from 'lucide-react';

import { formatCurrency, formatDateTime } from '../../app/format';
import { INTERACTION_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { PriorityBadge, ScoreIndicator } from '../../components/Badges';
import { link } from '../../components/styles';
import type { BoardCard } from '../../types/crm';
import type { LeadStatus } from '../../types/lead';
import { StatusChangeForm } from '../leads/StatusChangeForm';
import { daysLabel, signalsOf } from './pipelineSignals';

/** Card com o formulário de mudança aberto e, se veio de arrastar, o destino. */
export interface Moving {
  readonly leadId: string;
  readonly target?: LeadStatus;
}

export type Density = 'compact' | 'detailed';

interface PipelineCardProps {
  readonly card: BoardCard;
  readonly density: Density;
  /** Instante de referência dos sinais (dias na etapa, atrasos). */
  readonly now: number;
  readonly moving: Moving | null;
  readonly onDragStart: () => void;
  readonly onDragEnd: () => void;
  readonly onMove: (moving: Moving | null) => void;
  readonly onMoved: () => void;
}

/**
 * Card do Kanban. Compacto: empresa, cidade, valor e sinais de ação.
 * Detalhado: também segmento, prioridade, score, serviço, última interação e
 * próxima tarefa. "Mover" é um ícone no canto, sempre alcançável pelo teclado.
 */
export function PipelineCard({
  card,
  density,
  now,
  moving,
  onDragStart,
  onDragEnd,
  onMove,
  onMoved,
}: PipelineCardProps): React.JSX.Element {
  const draggable = card.allowedTransitions.length > 0;
  const signals = signalsOf(card, now);
  const detailed = density === 'detailed';
  const place = [detailed ? card.segment : undefined, `${card.city}/${card.state}`].filter(
    (part) => part !== undefined,
  );

  return (
    // Arrastar é atalho de mouse; o teclado usa o botão "Mover".
    // oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <article
      draggable={draggable}
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', card.leadId);
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      className={`group relative rounded-lg border border-slate-200 bg-white text-sm shadow-sm ${
        detailed ? 'p-2.5' : 'px-2.5 py-1.5'
      } ${draggable ? 'cursor-grab' : ''}`}
    >
      <div className="pr-7">
        <Link
          to={`/leads/${card.leadId}`}
          className="line-clamp-2 text-[13px] leading-snug font-semibold break-words text-slate-900 hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
        >
          {card.companyName}
        </Link>
        <p className="truncate text-xs text-slate-500">{place.join(' · ')}</p>
      </div>

      {draggable && moving === null && (
        <button
          type="button"
          aria-label={`Mover ${card.companyName}`}
          title="Mover para outra etapa"
          className="absolute top-2 right-2 rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-2 focus-visible:outline-brand-500 md:opacity-0 md:group-focus-within:opacity-100 md:group-hover:opacity-100"
          onClick={() => {
            onMove({ leadId: card.leadId });
          }}
        >
          <ArrowRightLeft aria-hidden="true" className="size-4" />
        </button>
      )}

      {(card.estimatedValue !== undefined || detailed) && (
        <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1">
          {card.estimatedValue !== undefined && (
            <span className="text-xs font-semibold text-slate-900 tabular-nums">
              {formatCurrency(card.estimatedValue)}
            </span>
          )}
          {detailed && <PriorityBadge priority={card.priority} />}
          {/* Sem análise não é informação: o score só aparece quando existe (D7). */}
          {detailed && card.score !== undefined && (
            <span className="text-xs text-slate-500">
              Score <ScoreIndicator score={card.score} />
            </span>
          )}
        </div>
      )}

      <Signals signals={signals} />

      {detailed && (
        <div className="mt-1.5 space-y-0.5 text-xs text-slate-600">
          {card.service !== undefined && <p>{`Serviço: ${card.service}`}</p>}
          {card.lastInteractionType !== undefined && card.lastInteractionAt !== undefined && (
            <p>{`Última interação: ${INTERACTION_TYPE_LABEL[card.lastInteractionType]} em ${formatDateTime(card.lastInteractionAt)}`}</p>
          )}
          {card.nextTaskTitle !== undefined && (
            <p>
              {`Próxima tarefa: ${card.nextTaskTitle}${card.nextTaskDueAt === undefined ? '' : ` até ${formatDateTime(card.nextTaskDueAt)}`}`}
            </p>
          )}
        </div>
      )}

      {moving !== null && (
        <div className="mt-3 border-t border-slate-200 pt-3">
          <StatusChangeForm
            key={moving.target ?? ''}
            lead={{ id: card.leadId, status: card.status, allowedTransitions: card.allowedTransitions }}
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
      )}
    </article>
  );
}

/**
 * Sinais de ação, cada um com ícone e texto: nunca só cor. Lead que entrou na
 * etapa hoje não ganha selo de tempo: não pede ação e só ocuparia espaço.
 */
function Signals({ signals }: { readonly signals: ReturnType<typeof signalsOf> }): React.JSX.Element | null {
  const { daysInStage, stale, overdueTask, quietDays } = signals;
  if (!overdueTask && daysInStage === 0 && quietDays === undefined) {
    return null;
  }
  return (
    <ul aria-label="Situação do lead" className="mt-1.5 flex flex-wrap gap-1">
      {overdueTask && (
        <Signal tone="bg-red-50 text-red-700 ring-red-200" icon={<AlarmClock className="size-3" />}>
          Tarefa atrasada
        </Signal>
      )}
      {daysInStage > 0 && (
        <Signal
          tone={stale ? 'bg-amber-50 text-amber-800 ring-amber-200' : 'bg-slate-50 text-slate-500 ring-slate-200'}
          icon={<Clock className="size-3" />}
        >
          {`${daysLabel(daysInStage)} na etapa`}
        </Signal>
      )}
      {quietDays !== undefined && (
        <Signal tone="bg-slate-100 text-slate-700 ring-slate-200" icon={<MessageSquareOff className="size-3" />}>
          {`Sem interação há ${daysLabel(quietDays)}`}
        </Signal>
      )}
    </ul>
  );
}

function Signal({
  tone,
  icon,
  children,
}: {
  readonly tone: string;
  readonly icon: React.ReactNode;
  readonly children: React.ReactNode;
}): React.JSX.Element {
  return (
    <li className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium ring-1 ${tone}`}>
      <span aria-hidden="true">{icon}</span>
      {children}
    </li>
  );
}
