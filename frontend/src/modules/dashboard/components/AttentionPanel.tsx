import { AlarmClock, CircleCheck, Clock, FileText, ListPlus, type LucideIcon } from 'lucide-react';

import { formatDateTime } from '../../../app/format';
import { STATUS_LABEL } from '../../../app/labels';
import { Link } from '../../../app/router';
import { card } from '../../../components/styles';
import type { Atencao, ItemAtencao, SecaoAtencao } from '../model/dashboardData';

interface SectionSpec {
  readonly key: keyof Atencao;
  readonly title: string;
  /** Por que o item está aqui; aparece sob o nome da empresa. */
  readonly reason: (item: ItemAtencao, now: number) => string;
  readonly icon: LucideIcon;
  readonly tone: string;
}

const DAY = 86_400_000;

function daysAgo(iso: string, now: number): number {
  return Math.max(0, Math.floor((now - Date.parse(iso)) / DAY));
}

const SECTIONS: readonly SectionSpec[] = [
  {
    key: 'tarefas',
    title: 'Tarefas atrasadas ou de hoje',
    reason: (item) => `${item.detalhe ?? 'Tarefa'} · ${item.atrasada ? 'venceu' : 'vence'} ${formatDateTime(item.em)}`,
    icon: AlarmClock,
    tone: 'bg-red-50 text-red-700',
  },
  {
    key: 'parados',
    title: 'Parados há 7 dias ou mais',
    reason: (item, now) => `${STATUS_LABEL[item.status]} há ${daysAgo(item.em, now)} dias`,
    icon: Clock,
    tone: 'bg-amber-50 text-amber-700',
  },
  {
    key: 'propostasFrias',
    title: 'Propostas sem contato há 7 dias',
    reason: (item, now) => `Último contato há ${daysAgo(item.em, now)} dias`,
    icon: FileText,
    tone: 'bg-violet-50 text-violet-700',
  },
  {
    key: 'semTarefa',
    title: 'Em andamento sem próxima tarefa',
    reason: (item) => STATUS_LABEL[item.status],
    icon: ListPlus,
    tone: 'bg-sky-50 text-sky-700',
  },
];

/**
 * "Precisa de atenção": o que fazer agora, antes dos números do período.
 * Cada linha leva ao lead; vazio é boa notícia e diz isso.
 */
export function AttentionPanel({
  atencao,
  now,
}: {
  readonly atencao: Atencao;
  /** Instante de referência para "há N dias". */
  readonly now: number;
}): React.JSX.Element {
  const total = SECTIONS.reduce((sum, spec) => sum + atencao[spec.key].total, 0);
  // Só o que tem pendência vira cartão; o resto cabe numa linha de "em dia".
  const pending = SECTIONS.filter((spec) => atencao[spec.key].total > 0);
  const clear = SECTIONS.filter((spec) => atencao[spec.key].total === 0);

  return (
    <section id="atencao" aria-labelledby="atencao-titulo" className="scroll-mt-4 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <div className="flex items-center gap-2">
          <h2
            id="atencao-titulo"
            className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase"
          >
            Precisa de atenção
          </h2>
          <span
            className={`rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums ${
              total === 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
            }`}
          >
            {total === 0 ? 'tudo em dia' : `${total} pendentes`}
          </span>
        </div>
        {clear.length > 0 && (
          <ul aria-label="Em dia" className="flex flex-wrap gap-1.5">
            {clear.map((spec) => (
              <li
                key={spec.key}
                className="inline-flex items-center gap-1 rounded-full glass px-2.5 py-1 text-[11px] font-medium text-stone-500 ring-1 ring-stone-200/70"
              >
                <CircleCheck aria-hidden="true" className="size-3.5 text-emerald-700" />
                {`${spec.title}: nenhum`}
              </li>
            ))}
          </ul>
        )}
      </div>
      {pending.length > 0 && (
        <div className={`grid items-start gap-4 ${CARD_GRID[pending.length] ?? CARD_GRID[4]}`}>
          {pending.map((spec) => (
            <AttentionCard
              key={spec.key}
              spec={spec}
              secao={atencao[spec.key]}
              now={now}
              listGrid={ITEM_GRID[pending.length] ?? ''}
            />
          ))}
        </div>
      )}
    </section>
  );
}

/** Cartões por linha conforme quantas seções têm pendência: nada de cartão vazio ocupando espaço. */
const CARD_GRID: Readonly<Record<number, string>> = {
  1: 'grid-cols-1',
  2: 'md:grid-cols-2',
  3: 'md:grid-cols-2 xl:grid-cols-3',
  4: 'sm:grid-cols-2 xl:grid-cols-4',
};

/** Com poucas seções o cartão é largo: os itens se espalham em colunas em vez de virar uma lista estreita. */
const ITEM_GRID: Readonly<Record<number, string>> = {
  1: 'sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5',
  2: 'lg:grid-cols-2',
};

function AttentionCard({
  spec,
  secao,
  now,
  listGrid,
}: {
  readonly spec: SectionSpec;
  readonly secao: SecaoAtencao;
  readonly now: number;
  readonly listGrid: string;
}): React.JSX.Element {
  const Icon = spec.icon;
  const headingId = `atencao-${spec.key}`;

  return (
    <section aria-labelledby={headingId} className={`${card} flex flex-col p-4`}>
      <div className="flex items-start justify-between gap-2">
        <h3 id={headingId} className="flex items-center gap-2 text-xs font-semibold text-stone-700">
          <span aria-hidden="true" className={`rounded-lg p-1.5 ${spec.tone}`}>
            <Icon className="size-3.5" />
          </span>
          {spec.title}
        </h3>
        <span className="text-sm font-bold text-stone-900 tabular-nums">{secao.total}</span>
      </div>

      <ul className={`mt-3 grid gap-x-6 gap-y-2 ${listGrid}`}>
        {secao.itens.map((item) => (
          <li key={`${spec.key}-${item.leadId}-${item.em}`} className="min-w-0">
            <Link
              to={`/leads/${item.leadId}`}
              className="block truncate text-sm font-medium text-stone-900 hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
            >
              {item.empresa}
            </Link>
            <p className={`truncate text-xs ${item.atrasada ? 'font-medium text-red-700' : 'text-stone-500'}`}>
              {spec.reason(item, now)}
            </p>
          </li>
        ))}
      </ul>

      {secao.total > secao.itens.length && (
        <p className="mt-3 border-t border-stone-100 pt-2 text-xs text-stone-500">
          {`Mais ${secao.total - secao.itens.length} no `}
          <Link to="/pipeline" className="font-medium text-brand-700 hover:underline">
            pipeline
          </Link>
        </p>
      )}
    </section>
  );
}
