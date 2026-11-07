import type { BoardCard } from '../../types/crm';
import type { LeadStatus } from '../../types/lead';

/**
 * Action signals for the Kanban cards: what is stalled and what needs doing.
 * Pure rules over the data the board already carries, with no implicit clock,
 * so a test can pin "now".
 */

const DAY = 86_400_000;

/** Days in an in-progress stage after which the lead counts as stalled. */
export const STALE_DAYS = 7;

/** Days without a recorded interaction after which the lead has gone cold. */
export const QUIET_DAYS = 14;

/** Closed stages do not "stall": time spent in them calls for nothing. */
const CLOSED: ReadonlySet<LeadStatus> = new Set(['WON', 'LOST']);

export interface CardSignals {
  /** Whole days in the current stage. */
  readonly daysInStage: number;
  readonly stale: boolean;
  readonly overdueTask: boolean;
  /** Days since the last interaction, when there is one and it is past the threshold. */
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
