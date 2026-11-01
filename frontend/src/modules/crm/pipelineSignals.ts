import type { BoardCard } from '../../types/crm';
import type { LeadStatus } from '../../types/lead';

/**
 * Sinais de ação dos cards do Kanban: o que está parado e o que precisa ser
 * feito. Regras puras, sobre os dados que o quadro já traz; sem relógio
 * implícito, para o teste fixar o "agora".
 */

const DAY = 86_400_000;

/** Dias numa etapa em andamento a partir dos quais o lead está parado. */
export const STALE_DAYS = 7;

/** Dias sem interação registrada a partir dos quais o lead esfriou. */
export const QUIET_DAYS = 14;

/** Etapas encerradas não "param": o tempo nelas não pede ação. */
const CLOSED: ReadonlySet<LeadStatus> = new Set(['WON', 'LOST']);

export interface CardSignals {
  /** Dias inteiros na etapa atual. */
  readonly daysInStage: number;
  readonly stale: boolean;
  readonly overdueTask: boolean;
  /** Dias desde a última interação, quando ela existe e já passou do limite. */
  readonly quietDays?: number;
}

export function signalsOf(card: BoardCard, now: number): CardSignals {
  const daysInStage = daysSince(card.stageEnteredAt, now) ?? 0;
  const open = !CLOSED.has(card.status);
  const due = card.nextTaskDueAt === undefined ? Number.NaN : Date.parse(card.nextTaskDueAt);
  const quiet = daysSince(card.lastInteractionAt, now);
  return {
    daysInStage,
    stale: open && daysInStage >= STALE_DAYS,
    overdueTask: open && !Number.isNaN(due) && due < now,
    ...(open && quiet !== undefined && quiet >= QUIET_DAYS ? { quietDays: quiet } : {}),
  };
}

function daysSince(iso: string | undefined, now: number): number | undefined {
  if (iso === undefined) {
    return undefined;
  }
  const at = Date.parse(iso);
  return Number.isNaN(at) ? undefined : Math.max(0, Math.floor((now - at) / DAY));
}

/** "hoje", "1 dia", "12 dias". */
export function daysLabel(days: number): string {
  if (days === 0) {
    return 'hoje';
  }
  return days === 1 ? '1 dia' : `${days} dias`;
}
