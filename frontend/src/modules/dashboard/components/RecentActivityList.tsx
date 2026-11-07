import {
  ArrowRight,
  Ban,
  CalendarDays,
  CircleCheck,
  CircleX,
  FileText,
  MessageSquareReply,
  Phone,
  Plus,
  StickyNote,
  type LucideIcon,
} from 'lucide-react';

import { formatDateTime } from '../../../app/format';
import { CHANNEL_LABEL, INTERACTION_TYPE_LABEL, STATUS_LABEL } from '../../../app/labels';
import { Link } from '../../../app/router';
import { card } from '../../../components/styles';
import type { RecentActivity, Transition } from '../model/dashboardData';

/** A soft tone per kind of event, from the application palette. */
type Tone = 'emerald' | 'brand' | 'sky' | 'violet' | 'slate' | 'red';

const TONE: Readonly<Record<Tone, { readonly icon: string; readonly badge: string }>> = {
  emerald: {
    icon: 'bg-emerald-50 text-emerald-700 ring-emerald-100',
    badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  },
  brand: { icon: 'bg-brand-50 text-brand-700 ring-brand-100', badge: 'bg-brand-50 text-brand-700 ring-brand-100' },
  sky: { icon: 'bg-sky-50 text-sky-700 ring-sky-100', badge: 'bg-sky-50 text-sky-700 ring-sky-200' },
  violet: {
    icon: 'bg-violet-50 text-violet-700 ring-violet-100',
    badge: 'bg-violet-50 text-violet-700 ring-violet-200',
  },
  slate: { icon: 'bg-stone-100 text-stone-500 ring-stone-200', badge: 'bg-stone-100 text-stone-600 ring-stone-200' },
  red: { icon: 'bg-red-50 text-red-700 ring-red-100', badge: 'bg-red-50 text-red-700 ring-red-200' },
};

interface Presentation {
  readonly icon: LucideIcon;
  readonly tone: Tone;
  /** The tag on the right: the stage reached, or the interaction type. */
  readonly etiqueta: string;
  /** The detail line under the company name. */
  readonly detail: string;
}

/** The latest changes in compact cards. Presentation only: it loads nothing. */
export function RecentActivityList({
  atividades,
}: {
  readonly atividades: readonly RecentActivity[];
}): React.JSX.Element {
  if (atividades.length === 0) {
    return <p className="text-sm text-stone-600">Nenhuma atividade no período.</p>;
  }
  return (
    // A single divided list: six activities fit where four cards used to.
    <ul className={`${card} divide-y divide-stone-100`}>
      {atividades.map((atividade) => (
        <ActivityItem key={atividade.id} atividade={atividade} />
      ))}
    </ul>
  );
}

function ActivityItem({ atividade }: { readonly atividade: RecentActivity }): React.JSX.Element {
  const { icon: Icon, tone, etiqueta, detail } = apresentar(atividade.transition);
  const quem = atividade.user === undefined ? '' : ` · ${atividade.user}`;

  return (
    <li className="flex items-center gap-2.5 px-3 py-2">
      <span
        aria-hidden="true"
        className={`flex size-7 shrink-0 items-center justify-center rounded-lg ring-1 ${TONE[tone].icon}`}
      >
        <Icon className="size-3.5" strokeWidth={2} />
      </span>
      <div className="min-w-0 flex-1">
        <Link
          to={`/leads/${atividade.leadId}`}
          className="block truncate text-sm font-semibold text-stone-900 hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
        >
          {atividade.company}
        </Link>
        <p className="truncate text-xs text-stone-500">{`${detail}${quem}`}</p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-0.5">
        <span className={`rounded-md px-2 py-0.5 text-[11px] font-semibold ring-1 ${TONE[tone].badge}`}>
          {etiqueta}
        </span>
        <time
          dateTime={atividade.at}
          title={formatDateTime(atividade.at)}
          className="text-[11px] text-stone-400 tabular-nums"
        >
          {horario(atividade.at)}
        </time>
      </div>
    </li>
  );
}

function apresentar(transition: Transition): Presentation {
  if (transition.kind === 'interaction') {
    const tipo = transition.interaction;
    const nome = tipo === undefined ? 'Interação' : INTERACTION_TYPE_LABEL[tipo];
    const detail = transition.channel === undefined ? nome : `${nome} por ${CHANNEL_LABEL[transition.channel]}`;
    const visual: Readonly<Record<NonNullable<typeof tipo>, readonly [LucideIcon, Tone]>> = {
      CONTACT: [Phone, 'sky'],
      REPLY: [MessageSquareReply, 'sky'],
      MEETING: [CalendarDays, 'sky'],
      PROPOSAL: [FileText, 'violet'],
      DO_NOT_CONTACT_REQUEST: [Ban, 'red'],
      NOTE: [StickyNote, 'slate'],
    };
    const [icon, tone] = tipo === undefined ? [StickyNote, 'slate' as const] : visual[tipo];
    return { icon, tone, etiqueta: nome, detail };
  }

  const para = transition.to;
  const destino = para === undefined ? '—' : STATUS_LABEL[para];
  if (transition.from === undefined) {
    return { icon: Plus, tone: 'brand', etiqueta: destino, detail: 'Lead aberto' };
  }
  const detail = `${STATUS_LABEL[transition.from]} → ${destino}`;
  if (para === 'WON') {
    return { icon: CircleCheck, tone: 'emerald', etiqueta: destino, detail };
  }
  if (para === 'LOST') {
    return { icon: CircleX, tone: 'slate', etiqueta: destino, detail };
  }
  if (para === 'DO_NOT_CONTACT') {
    return { icon: Ban, tone: 'red', etiqueta: destino, detail };
  }
  return { icon: ArrowRight, tone: 'brand', etiqueta: destino, detail };
}

const HOUR = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });
const DAY = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });

/** Time only for today's events, day and month for older ones. The full date is in the `title`. */
function horario(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return date.toDateString() === new Date().toDateString() ? HOUR.format(date) : DAY.format(date);
}
