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
import type { AtividadeRecente, Transicao } from '../model/dashboardData';

/** Tom suave por natureza do evento, da paleta da aplicação. */
type Tom = 'emerald' | 'brand' | 'sky' | 'violet' | 'slate' | 'red';

const TONE: Readonly<Record<Tom, { readonly icon: string; readonly badge: string }>> = {
  emerald: {
    icon: 'bg-emerald-50 text-emerald-600 ring-emerald-100',
    badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  },
  brand: { icon: 'bg-brand-50 text-brand-600 ring-brand-100', badge: 'bg-brand-50 text-brand-700 ring-brand-100' },
  sky: { icon: 'bg-sky-50 text-sky-600 ring-sky-100', badge: 'bg-sky-50 text-sky-700 ring-sky-200' },
  violet: {
    icon: 'bg-violet-50 text-violet-600 ring-violet-100',
    badge: 'bg-violet-50 text-violet-700 ring-violet-200',
  },
  slate: { icon: 'bg-stone-100 text-stone-500 ring-stone-200', badge: 'bg-stone-100 text-stone-600 ring-stone-200' },
  red: { icon: 'bg-red-50 text-red-600 ring-red-100', badge: 'bg-red-50 text-red-700 ring-red-200' },
};

interface Apresentacao {
  readonly icone: LucideIcon;
  readonly tom: Tom;
  /** Etiqueta à direita: a etapa de chegada ou o tipo de interação. */
  readonly etiqueta: string;
  /** Linha de detalhe sob o nome da empresa. */
  readonly detalhe: string;
}

/** Últimas alterações em cartões compactos. Só apresenta: não carrega nada. */
export function RecentActivityList({
  atividades,
}: {
  readonly atividades: readonly AtividadeRecente[];
}): React.JSX.Element {
  if (atividades.length === 0) {
    return <p className="text-sm text-stone-600">Nenhuma atividade no período.</p>;
  }
  return (
    // Uma lista só, com divisórias: seis atividades cabem onde antes cabiam quatro cartões.
    <ul className={`${card} divide-y divide-stone-100`}>
      {atividades.map((atividade) => (
        <ActivityItem key={atividade.id} atividade={atividade} />
      ))}
    </ul>
  );
}

function ActivityItem({ atividade }: { readonly atividade: AtividadeRecente }): React.JSX.Element {
  const { icone: Icon, tom, etiqueta, detalhe } = apresentar(atividade.transicao);
  const quem = atividade.usuario === undefined ? '' : ` · ${atividade.usuario}`;

  return (
    <li className="flex items-center gap-2.5 px-3 py-2">
      <span
        aria-hidden="true"
        className={`flex size-7 shrink-0 items-center justify-center rounded-lg ring-1 ${TONE[tom].icon}`}
      >
        <Icon className="size-3.5" strokeWidth={2} />
      </span>
      <div className="min-w-0 flex-1">
        <Link
          to={`/leads/${atividade.leadId}`}
          className="block truncate text-sm font-semibold text-stone-900 hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
        >
          {atividade.empresa}
        </Link>
        <p className="truncate text-xs text-stone-500">{`${detalhe}${quem}`}</p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-0.5">
        <span className={`rounded-md px-2 py-0.5 text-[11px] font-semibold ring-1 ${TONE[tom].badge}`}>{etiqueta}</span>
        <time
          dateTime={atividade.data}
          title={formatDateTime(atividade.data)}
          className="text-[11px] text-stone-400 tabular-nums"
        >
          {horario(atividade.data)}
        </time>
      </div>
    </li>
  );
}

function apresentar(transicao: Transicao): Apresentacao {
  if (transicao.tipo === 'interacao') {
    const tipo = transicao.interacao;
    const nome = tipo === undefined ? 'Interação' : INTERACTION_TYPE_LABEL[tipo];
    const detalhe = transicao.canal === undefined ? nome : `${nome} por ${CHANNEL_LABEL[transicao.canal]}`;
    const visual: Readonly<Record<NonNullable<typeof tipo>, readonly [LucideIcon, Tom]>> = {
      CONTACT: [Phone, 'sky'],
      REPLY: [MessageSquareReply, 'sky'],
      MEETING: [CalendarDays, 'sky'],
      PROPOSAL: [FileText, 'violet'],
      DO_NOT_CONTACT_REQUEST: [Ban, 'red'],
      NOTE: [StickyNote, 'slate'],
    };
    const [icone, tom] = tipo === undefined ? [StickyNote, 'slate' as const] : visual[tipo];
    return { icone, tom, etiqueta: nome, detalhe };
  }

  const para = transicao.para;
  const destino = para === undefined ? '—' : STATUS_LABEL[para];
  if (transicao.de === undefined) {
    return { icone: Plus, tom: 'brand', etiqueta: destino, detalhe: 'Lead aberto' };
  }
  const detalhe = `${STATUS_LABEL[transicao.de]} → ${destino}`;
  if (para === 'WON') {
    return { icone: CircleCheck, tom: 'emerald', etiqueta: destino, detalhe };
  }
  if (para === 'LOST') {
    return { icone: CircleX, tom: 'slate', etiqueta: destino, detalhe };
  }
  if (para === 'DO_NOT_CONTACT') {
    return { icone: Ban, tom: 'red', etiqueta: destino, detalhe };
  }
  return { icone: ArrowRight, tom: 'brand', etiqueta: destino, detalhe };
}

const HOUR = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' });
const DAY = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });

/** Só a hora para eventos de hoje; dia e mês para os anteriores. A data completa fica no `title`. */
function horario(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return date.toDateString() === new Date().toDateString() ? HOUR.format(date) : DAY.format(date);
}
