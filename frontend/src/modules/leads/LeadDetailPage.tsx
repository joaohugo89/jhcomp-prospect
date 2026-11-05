import {
  AlarmClock,
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  ListPlus,
  MessageSquarePlus,
  Send,
  Shuffle,
} from 'lucide-react';
import { useEffect, useEffectEvent, useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatCurrency, formatDateTime } from '../../app/format';
import { PRIORITY_LABEL } from '../../app/labels';
import { Link, navigate } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { ScoreIndicator, StatusBadge } from '../../components/Badges';
import { LoadStateView } from '../../components/States';
import { card, input, link } from '../../components/styles';
import { changeLeadPriority, getLead } from '../../services/leadService';
import { getLeadSummary } from '../../services/worklistService';
import { LEAD_PRIORITIES, type Lead } from '../../types/lead';
import type { WorklistItem } from '../../types/worklist';
import { AnalysisSection } from '../analysis/AnalysisSection';
import { daysLabel, STALE_DAYS } from '../crm/pipelineSignals';
import { DealSection } from '../crm/DealSection';
import { InteractionForm } from '../crm/InteractionForm';
import { TasksSection } from '../crm/TasksSection';
import { OutreachSection } from '../outreach/OutreachSection';
import { ActivityTimeline } from './ActivityTimeline';
import { AssigneeField } from './AssigneeField';
import { neighbours, readNavigation } from './leadNavigation';
import { StatusChangeForm } from './StatusChangeForm';

type Tab = 'history' | 'analysis' | 'messages';

const TABS: readonly [Tab, string][] = [
  ['history', 'Histórico'],
  ['analysis', 'Análise'],
  ['messages', 'Mensagens'],
];

const DAY = 86_400_000;

/** Foca o primeiro campo de um bloco e o traz para a tela (ações e atalhos). */
function focusIn(selector: string): void {
  window.requestAnimationFrame(() => {
    const target = document.querySelector<HTMLElement>(selector);
    target?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    target?.focus({ preventScroll: true });
  });
}

/** Atalho só fora de campo de texto: digitar "n" numa nota não pode trocar de aba. */
function isTyping(target: EventTarget | null): boolean {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  );
}

/**
 * Detalhe do lead (doc 08 §7, D31).
 *
 * Topo: o essencial sem rolar (status, valor, responsável, próxima tarefa,
 * tempo na etapa, score) e as quatro ações do dia a dia. Lateral fixa: o
 * estado do lead (propriedades, negócio, tarefas). Centro: registrar e ver o
 * histórico no mesmo lugar; análise e mensagens em abas. Atalhos: n
 * (interação), t (tarefa), j/k (próximo/anterior da lista).
 */
export function LeadDetailPage({ id }: { readonly id: string }): React.JSX.Element {
  const [state, reload] = useLoad(id, (signal) => getLead(id, signal));
  // Lead alterado nesta tela substitui o carregado, sem nova ida à API.
  const [updated, setUpdated] = useState<Lead | null>(null);
  // Interação registrada não altera o lead, mas precisa aparecer no histórico.
  const [interactions, setInteractions] = useState(0);
  const [summaryVersion, setSummaryVersion] = useState(0);
  const [summary] = useLoad(`${id}|${summaryVersion}`, async (signal) => ({
    item: await getLeadSummary(id, signal),
    receivedAt: Date.now(),
  }));
  const [tab, setTab] = useState<Tab>('history');
  const [navigation] = useState(readNavigation);
  const around = neighbours(navigation, id);

  const refreshSummary = (): void => {
    setSummaryVersion((value) => value + 1);
  };
  const changed = (lead: Lead): void => {
    setUpdated(lead);
    refreshSummary();
  };

  const actions = {
    interaction: (): void => {
      setTab('history');
      focusIn('#registrar-interacao select, #registrar-interacao input');
    },
    task: (): void => {
      focusIn('#tarefas-lead form input');
    },
    message: (): void => {
      setTab('messages');
      focusIn('#aba-messages');
    },
    move: (): void => {
      focusIn('#mover-etapa select');
    },
  };

  const onKey = useEffectEvent((event: KeyboardEvent) => {
    if (event.ctrlKey || event.metaKey || event.altKey || isTyping(event.target)) {
      return;
    }
    if (event.key === 'n') {
      event.preventDefault();
      actions.interaction();
    } else if (event.key === 't') {
      event.preventDefault();
      actions.task();
    } else if (event.key === 'j' && around?.next !== undefined) {
      navigate(`/leads/${around.next}`);
    } else if (event.key === 'k' && around?.previous !== undefined) {
      navigate(`/leads/${around.previous}`);
    }
  });
  useEffect(() => {
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
    };
  }, []);

  if (state.kind !== 'ready') {
    return <LoadStateView state={state} onRetry={reload} notFoundMessage="Lead não encontrado." />;
  }

  const lead = updated !== null && updated.id === state.data.id ? updated : state.data;
  const backTo = navigation === null || navigation.query === '' ? '/leads' : `/leads?${navigation.query}`;

  return (
    <div className="space-y-5">
      <nav aria-label="Navegação entre leads" className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <Link to={backTo} className="inline-flex items-center gap-1 font-medium text-slate-600 hover:text-slate-900">
          <ArrowLeft aria-hidden="true" className="size-4" />
          Voltar para a lista
        </Link>
        {around !== null && (
          <div className="flex items-center gap-1">
            <span className="mr-2 text-xs text-slate-500">{`${around.position} de ${around.total} na lista`}</span>
            <NeighbourLink
              to={around.previous}
              label="Lead anterior"
              shortcut="k"
              icon={<ChevronLeft className="size-4" />}
            />
            <NeighbourLink
              to={around.next}
              label="Próximo lead"
              shortcut="j"
              icon={<ChevronRight className="size-4" />}
            />
          </div>
        )}
      </nav>

      <header className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="text-xl font-bold tracking-tight break-words text-slate-900">{lead.company.name}</h1>
            <p className="mt-1 text-sm text-slate-500">
              {[lead.company.segment ?? 'Sem segmento', `${lead.company.city}/${lead.company.state}`].join(' · ')}
              {' · '}
              <Link to={`/companies/${lead.company.id}`} className={link}>
                Ver empresa
              </Link>
            </p>
          </div>
          <ActionBar actions={actions} />
        </div>
        <SummaryStrip lead={lead} summary={summary.kind === 'ready' ? summary.data : null} />
      </header>

      <div className="grid gap-4 lg:grid-cols-[22rem_minmax(0,1fr)]">
        <aside aria-label="Estado do lead" className="space-y-4 lg:sticky lg:top-4 lg:self-start">
          <section id="mover-etapa" aria-labelledby="propriedades" className={`${card} space-y-3 p-4`}>
            <h2 id="propriedades" className="text-sm font-semibold text-slate-900">
              Propriedades
            </h2>
            <StatusChangeForm lead={lead} onChanged={changed} />
            {/* Prioridade e responsável lado a lado: cabem na lateral e economizam uma linha cada. */}
            <div className="grid grid-cols-2 gap-3">
              <PriorityField lead={lead} onChanged={changed} />
              <AssigneeField lead={lead} onChanged={changed} />
            </div>
            <p className="border-t border-slate-100 pt-2 text-xs text-slate-500">
              {`Aberto em ${formatDateTime(lead.createdAt)} · atualizado em ${formatDateTime(lead.updatedAt)}${
                lead.closedAt === undefined ? '' : ` · encerrado em ${formatDateTime(lead.closedAt)}`
              }`}
            </p>
          </section>

          <DealSection lead={lead} onChanged={refreshSummary} />

          <div id="tarefas-lead">
            <TasksSection leadId={lead.id} onChanged={refreshSummary} />
          </div>
        </aside>

        <div className="min-w-0">
          <div role="tablist" aria-label="Seções do lead" className="mb-4 flex gap-1 border-b border-slate-200">
            {TABS.map(([key, label], index) => (
              <button
                key={key}
                id={`tab-${key}`}
                type="button"
                role="tab"
                aria-selected={tab === key}
                aria-controls={`aba-${key}`}
                tabIndex={tab === key ? 0 : -1}
                className={`-mb-px border-b-2 px-4 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-brand-500 ${
                  tab === key
                    ? 'border-brand-600 text-brand-700'
                    : 'border-transparent text-slate-500 hover:text-slate-800'
                }`}
                onClick={() => {
                  setTab(key);
                }}
                onKeyDown={(event) => {
                  // Setas trocam de aba, como no padrão de abas do WAI-ARIA.
                  const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
                  const next = TABS[(index + step + TABS.length) % TABS.length];
                  if (step !== 0 && next !== undefined) {
                    event.preventDefault();
                    setTab(next[0]);
                    document.getElementById(`tab-${next[0]}`)?.focus();
                  }
                }}
              >
                {label}
              </button>
            ))}
          </div>

          {/* Abas inativas ficam montadas e escondidas: rascunho de mensagem ou de interação não se perde. */}
          <div
            id="aba-history"
            role="tabpanel"
            aria-labelledby="tab-history"
            hidden={tab !== 'history'}
            className="space-y-4"
          >
            <div id="registrar-interacao">
              <InteractionForm
                lead={lead}
                onRecorded={(type) => {
                  setInteractions((count) => count + 1);
                  refreshSummary();
                  if (type === 'DO_NOT_CONTACT_REQUEST') {
                    // O pedido muda o status no servidor: relê o lead.
                    setUpdated(null);
                    reload();
                  }
                }}
              />
            </div>
            <ActivityTimeline leadId={lead.id} refreshKey={`${lead.status}:${lead.updatedAt}:${interactions}`} />
          </div>
          <div id="aba-analysis" role="tabpanel" aria-labelledby="tab-analysis" hidden={tab !== 'analysis'}>
            <AnalysisSection
              lead={lead}
              onScoreChanged={() => {
                // O score mudou no servidor: descarta a cópia local e relê o lead.
                setUpdated(null);
                reload();
                refreshSummary();
              }}
            />
          </div>
          <div
            id="aba-messages"
            role="tabpanel"
            aria-labelledby="tab-messages"
            tabIndex={-1}
            hidden={tab !== 'messages'}
            className="focus:outline-none"
          >
            <OutreachSection
              lead={lead}
              onRecorded={() => {
                // Envio registrado vira interação e pode mudar o status: relê o lead.
                setInteractions((count) => count + 1);
                setUpdated(null);
                reload();
                refreshSummary();
              }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function NeighbourLink({
  to,
  label,
  shortcut,
  icon,
}: {
  readonly to: string | undefined;
  readonly label: string;
  readonly shortcut: string;
  readonly icon: React.ReactNode;
}): React.JSX.Element {
  const className = 'inline-flex items-center rounded-lg border border-slate-200 bg-white p-1.5 shadow-sm';
  if (to === undefined) {
    return (
      <span aria-hidden="true" className={`${className} text-slate-300`}>
        {icon}
      </span>
    );
  }
  return (
    <Link
      to={`/leads/${to}`}
      aria-label={`${label} (atalho ${shortcut})`}
      className={`${className} text-slate-600 hover:bg-slate-50`}
    >
      <span aria-hidden="true">{icon}</span>
    </Link>
  );
}

/** As quatro ações do dia a dia, sempre no topo. */
function ActionBar({
  actions,
}: {
  readonly actions: Readonly<Record<'interaction' | 'task' | 'message' | 'move', () => void>>;
}): React.JSX.Element {
  const items: readonly [keyof typeof actions, string, React.ReactNode, string | null][] = [
    ['interaction', 'Nova interação', <MessageSquarePlus key="i" className="size-4" />, 'n'],
    ['task', 'Nova tarefa', <ListPlus key="t" className="size-4" />, 't'],
    ['message', 'Gerar mensagem', <Send key="m" className="size-4" />, null],
    ['move', 'Mover etapa', <Shuffle key="e" className="size-4" />, null],
  ];
  return (
    <div role="group" aria-label="Ações do lead" className="flex flex-wrap gap-2">
      {items.map(([key, label, icon, shortcut], index) => (
        <button
          key={key}
          type="button"
          {...(shortcut === null ? {} : { 'aria-keyshortcuts': shortcut })}
          className={`inline-flex h-9 items-center gap-1.5 rounded-xl px-3 text-sm font-semibold shadow-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
            index === 0
              ? 'bg-brand-600 text-white hover:bg-brand-700'
              : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
          }`}
          onClick={actions[key]}
        >
          <span aria-hidden="true">{icon}</span>
          {label}
          {shortcut !== null && (
            <kbd
              aria-hidden="true"
              className={`rounded px-1 text-[10px] font-semibold ${index === 0 ? 'bg-white/20' : 'bg-slate-100 text-slate-500'}`}
            >
              {shortcut}
            </kbd>
          )}
        </button>
      ))}
    </div>
  );
}

/** O essencial do lead num relance, sem rolar (D31). */
function SummaryStrip({
  lead,
  summary,
}: {
  readonly lead: Lead;
  readonly summary: { readonly item: WorklistItem; readonly receivedAt: number } | null;
}): React.JSX.Element {
  const item = summary?.item;
  const now = summary?.receivedAt ?? 0;
  const days =
    item?.stageEnteredAt === undefined
      ? undefined
      : Math.max(0, Math.floor((now - Date.parse(item.stageEnteredAt)) / DAY));
  const overdue = item?.nextTaskDueAt !== undefined && Date.parse(item.nextTaskDueAt) < now;

  return (
    <dl aria-label="Resumo do lead" className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
      <Tile label="Status">
        <StatusBadge status={lead.status} />
      </Tile>
      <Tile label="Valor" muted={item?.estimatedValue === undefined}>
        {item?.estimatedValue === undefined ? 'Sem valor' : formatCurrency(item.estimatedValue)}
      </Tile>
      <Tile label="Responsável" muted={item?.assigneeName === undefined}>
        {item?.assigneeName ?? 'Sem responsável'}
      </Tile>
      <Tile
        label="Próxima tarefa"
        muted={item?.nextTaskTitle === undefined}
        tone={overdue ? 'text-red-700' : undefined}
      >
        {item?.nextTaskTitle === undefined ? (
          'Nenhuma'
        ) : (
          <span className="flex items-center gap-1">
            {overdue && <AlarmClock aria-hidden="true" className="size-3.5 shrink-0" />}
            <span className="truncate">{`${overdue ? 'Atrasada: ' : ''}${item.nextTaskTitle}`}</span>
          </span>
        )}
      </Tile>
      <Tile label="Na etapa" tone={days !== undefined && days >= STALE_DAYS ? 'text-amber-800' : undefined}>
        {days === undefined ? '—' : days === 0 ? 'desde hoje' : daysLabel(days)}
      </Tile>
      <Tile label="Score" muted={lead.score === undefined}>
        {lead.score === undefined ? 'Sem análise' : <ScoreIndicator score={lead.score} />}
      </Tile>
    </dl>
  );
}

function Tile({
  label,
  children,
  muted = false,
  tone,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
  readonly muted?: boolean;
  readonly tone?: string | undefined;
}): React.JSX.Element {
  return (
    <div className={`${card} min-w-0 px-4 py-3`}>
      <dt className="text-[11px] font-medium text-slate-500">{label}</dt>
      <dd
        className={`mt-1 truncate text-sm font-semibold ${muted ? 'font-normal text-slate-400' : (tone ?? 'text-slate-900')}`}
      >
        {children}
      </dd>
    </div>
  );
}

function PriorityField({
  lead,
  onChanged,
}: {
  readonly lead: Lead;
  readonly onChanged: (lead: Lead) => void;
}): React.JSX.Element {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function change(value: string): Promise<void> {
    const priority = LEAD_PRIORITIES.find((item) => item === value);
    if (priority === undefined || priority === lead.priority) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onChanged(await changeLeadPriority(lead.id, priority));
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <label className="block text-sm font-medium text-slate-700">
        Prioridade
        <select
          className={input}
          value={lead.priority}
          disabled={busy}
          onChange={(event) => {
            void change(event.target.value);
          }}
        >
          {LEAD_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {PRIORITY_LABEL[priority]}
            </option>
          ))}
        </select>
      </label>
      {error !== null && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
