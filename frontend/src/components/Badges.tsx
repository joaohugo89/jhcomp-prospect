import { PRIORITY_LABEL, STATUS_LABEL } from '../app/labels';
import type { LeadPriority, LeadStatus } from '../types/lead';

/**
 * Indicadores de status, prioridade e score.
 *
 * Todos trazem texto: a cor reforça, mas nunca é o único sinal (doc 08 §12).
 */

const STATUS_TONE: Readonly<Record<LeadStatus, string>> = {
  NEW: 'bg-slate-100 text-slate-800 ring-slate-300',
  QUALIFIED: 'bg-sky-50 text-sky-900 ring-sky-300',
  CONTACT_APPROVED: 'bg-indigo-50 text-indigo-900 ring-indigo-300',
  CONTACTED: 'bg-violet-50 text-violet-900 ring-violet-300',
  IN_CONVERSATION: 'bg-amber-50 text-amber-900 ring-amber-300',
  PROPOSAL_SENT: 'bg-orange-50 text-orange-900 ring-orange-300',
  WON: 'bg-emerald-50 text-emerald-900 ring-emerald-300',
  LOST: 'bg-zinc-100 text-zinc-700 ring-zinc-300',
  DO_NOT_CONTACT: 'bg-red-50 text-red-900 ring-red-300',
};

const badge = 'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset whitespace-nowrap';

export function StatusBadge({ status }: { readonly status: LeadStatus }): React.JSX.Element {
  return <span className={`${badge} ${STATUS_TONE[status]}`}>{STATUS_LABEL[status]}</span>;
}

const PRIORITY_TONE: Readonly<Record<LeadPriority, string>> = {
  LOW: 'bg-white text-slate-700 ring-slate-300',
  MEDIUM: 'bg-white text-slate-800 ring-slate-400',
  HIGH: 'bg-amber-50 text-amber-900 ring-amber-400',
  VERY_HIGH: 'bg-red-50 text-red-900 ring-red-400',
};

export function PriorityBadge({ priority }: { readonly priority: LeadPriority }): React.JSX.Element {
  return <span className={`${badge} ${PRIORITY_TONE[priority]}`}>{PRIORITY_LABEL[priority]}</span>;
}

/** Score ausente é "sem análise", nunca zero (D7). */
export function ScoreIndicator({ score }: { readonly score: number | undefined }): React.JSX.Element {
  if (score === undefined) {
    return <span className="text-xs text-slate-500">Sem análise</span>;
  }
  return (
    <span className="font-mono text-sm tabular-nums text-slate-900" aria-label={`Score ${score} de 100`}>
      {score}
    </span>
  );
}
