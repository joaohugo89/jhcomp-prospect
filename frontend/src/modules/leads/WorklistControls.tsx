import { Bookmark, BookmarkPlus, Users, X } from 'lucide-react';
import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { PRIORITY_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { buttonPrimary, buttonSecondary, chip, input } from '../../components/styles';
import { bulkUpdateLeads, createView, deleteView } from '../../services/worklistService';
import { LEAD_PRIORITIES } from '../../types/lead';
import type { Teammate } from '../../types/session';
import type { SavedView, WorklistFilters } from '../../types/worklist';

/** Recortes de um clique: os mesmos do painel de atenção do dashboard. */
type Quick = 'all' | 'mine' | 'unassigned' | 'overdue' | 'stale' | 'withoutTask';

const QUICK: readonly [Quick, string][] = [
  ['all', 'Todos'],
  ['mine', 'Meus'],
  ['unassigned', 'Sem responsável'],
  ['overdue', 'Tarefa atrasada'],
  ['stale', 'Parados'],
  ['withoutTask', 'Sem próxima tarefa'],
];

function quickOf(filters: WorklistFilters, currentUserId: string): Quick | null {
  if (filters.focus === 'OVERDUE') return 'overdue';
  if (filters.focus === 'STALE') return 'stale';
  if (filters.focus === 'WITHOUT_TASK') return 'withoutTask';
  if (filters.unassigned) return 'unassigned';
  if (filters.assignee === currentUserId) return 'mine';
  return filters.assignee === '' ? 'all' : null;
}

/** Aplica um recorte, mantendo os demais filtros (busca, segmento, etc.). */
function applyQuick(filters: WorklistFilters, quick: Quick, currentUserId: string): WorklistFilters {
  const base = { ...filters, focus: '' as const, unassigned: false, assignee: '' };
  switch (quick) {
    case 'mine':
      return { ...base, assignee: currentUserId };
    case 'unassigned':
      return { ...base, unassigned: true };
    case 'overdue':
      return { ...base, focus: 'OVERDUE' };
    case 'stale':
      return { ...base, focus: 'STALE' };
    case 'withoutTask':
      return { ...base, focus: 'WITHOUT_TASK' };
    default:
      return base;
  }
}

export function QuickFilters({
  filters,
  currentUserId,
  onApply,
}: {
  readonly filters: WorklistFilters;
  readonly currentUserId: string;
  readonly onApply: (filters: WorklistFilters) => void;
}): React.JSX.Element {
  const active = quickOf(filters, currentUserId);
  return (
    <div role="group" aria-label="Recortes rápidos" className="flex flex-wrap gap-1.5">
      {QUICK.map(([key, label]) => (
        <button
          key={key}
          type="button"
          aria-pressed={active === key}
          className={chip(active === key)}
          onClick={() => {
            onApply(applyQuick(filters, key, currentUserId));
          }}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

/**
 * Ações em lote sobre os leads selecionados: só responsável e prioridade.
 * Status fica fora de propósito: "Contatado" exige confirmar o contato lead a
 * lead (regra 6) e os estados finais pedem confirmação individual.
 */
export function BulkBar({
  selected,
  team,
  onDone,
  onClear,
}: {
  readonly selected: ReadonlySet<string>;
  readonly team: readonly Teammate[];
  readonly onDone: (message: string) => void;
  readonly onClear: () => void;
}): React.JSX.Element {
  const [assignee, setAssignee] = useState('');
  const [priority, setPriority] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ids = [...selected];

  async function run(change: Parameters<typeof bulkUpdateLeads>[1]): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      const { updated } = await bulkUpdateLeads(ids, change);
      onDone(`${updated} ${updated === 1 ? 'lead atualizado' : 'leads atualizados'}.`);
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      role="region"
      aria-label="Ações em lote"
      className="flex flex-wrap items-center gap-3 rounded-2xl bg-slate-900 px-4 py-3 text-sm text-white shadow-lg"
    >
      <span className="font-semibold">{`${selected.size} ${selected.size === 1 ? 'selecionado' : 'selecionados'}`}</span>
      <label className="flex items-center gap-2">
        <span className="sr-only">Responsável para os selecionados</span>
        <select
          className={`${input} mt-0 h-9 w-48 py-0 text-slate-900`}
          value={assignee}
          disabled={busy}
          onChange={(event) => {
            setAssignee(event.target.value);
          }}
        >
          <option value="">Atribuir a…</option>
          <option value="__nobody__">Sem responsável</option>
          {team.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.displayName}
            </option>
          ))}
        </select>
        <button
          type="button"
          className={`${buttonSecondary} h-9 py-0`}
          disabled={busy || assignee === ''}
          onClick={() => {
            void run({ action: 'ASSIGN', assignedUserId: assignee === '__nobody__' ? null : assignee });
          }}
        >
          Atribuir
        </button>
      </label>
      <label className="flex items-center gap-2">
        <span className="sr-only">Prioridade para os selecionados</span>
        <select
          className={`${input} mt-0 h-9 w-40 py-0 text-slate-900`}
          value={priority}
          disabled={busy}
          onChange={(event) => {
            setPriority(event.target.value);
          }}
        >
          <option value="">Prioridade…</option>
          {LEAD_PRIORITIES.map((item) => (
            <option key={item} value={item}>
              {PRIORITY_LABEL[item]}
            </option>
          ))}
        </select>
        <button
          type="button"
          className={`${buttonSecondary} h-9 py-0`}
          disabled={busy || priority === ''}
          onClick={() => {
            const chosen = LEAD_PRIORITIES.find((item) => item === priority);
            if (chosen !== undefined) {
              void run({ action: 'PRIORITY', priority: chosen });
            }
          }}
        >
          Alterar
        </button>
      </label>
      <button type="button" className="ml-auto text-xs font-semibold text-slate-300 hover:text-white" onClick={onClear}>
        Limpar seleção
      </button>
      {error !== null && (
        <p role="alert" className="basis-full text-sm text-red-200">
          {error}
        </p>
      )}
    </div>
  );
}

/**
 * Visões salvas de quem está usando: buscas que se repetem viram um clique e
 * valem em qualquer aparelho. Abrir uma visão é navegar para a URL dela.
 */
export function SavedViews({
  views,
  currentQuery,
  canShare,
  onChanged,
}: {
  readonly views: readonly SavedView[];
  readonly currentQuery: string;
  /** Administrador: pode publicar a visão para a equipe (D31). */
  readonly canShare: boolean;
  readonly onChanged: () => void;
}): React.JSX.Element {
  const [naming, setNaming] = useState(false);
  const [name, setName] = useState('');
  const [shared, setShared] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<SavedView | null>(null);
  const alreadySaved = views.some((view) => view.query === currentQuery);

  async function save(): Promise<void> {
    setError(null);
    try {
      await createView(name.trim(), currentQuery, canShare && shared);
      setNaming(false);
      setName('');
      setShared(false);
      onChanged();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'Já existe uma visão com esse nome, ou o limite de 20 foi atingido.'));
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="flex items-center gap-1 text-[11px] font-semibold tracking-wider text-slate-500 uppercase">
        <Bookmark aria-hidden="true" className="size-3.5" />
        Visões
      </span>
      {views.length === 0 && !naming && <span className="text-xs text-slate-400">Nenhuma salva</span>}
      <ul aria-label="Visões salvas" className="contents">
        {views.map((view) => (
          <li
            key={view.id}
            className={`inline-flex items-center rounded-full text-xs font-medium ring-1 ${
              view.query === currentQuery
                ? 'bg-brand-50 text-brand-800 ring-brand-100'
                : 'bg-white text-slate-700 ring-slate-200'
            }`}
          >
            <Link
              to={view.query === '' ? '/leads' : `/leads?${view.query}`}
              {...(view.shared ? { 'aria-label': `${view.name} (da equipe)` } : {})}
              className={`inline-flex items-center gap-1 rounded-l-full py-1 pl-3 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500 ${
                view.canDelete ? 'pr-1' : 'rounded-r-full pr-3'
              }`}
            >
              {view.shared && <Users aria-hidden="true" className="size-3 text-slate-500" />}
              {view.name}
            </Link>
            {/* Apagar só aparece quando o servidor aceitaria: compartilhada é do administrador. */}
            {view.canDelete && (
              <button
                type="button"
                aria-label={`Apagar a visão ${view.name}`}
                className="rounded-r-full py-1 pr-2 pl-1 text-slate-400 hover:text-red-700 focus-visible:outline-2 focus-visible:outline-brand-500"
                onClick={() => {
                  setRemoving(view);
                }}
              >
                <X aria-hidden="true" className="size-3" />
              </button>
            )}
          </li>
        ))}
      </ul>
      {naming ? (
        <form
          aria-label="Salvar visão"
          className="flex items-center gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (name.trim() !== '') {
              void save();
            }
          }}
        >
          <label>
            <span className="sr-only">Nome da visão</span>
            <input
              className={`${input} mt-0 h-8 w-48 py-0 text-xs`}
              placeholder="Ex.: Minhas propostas"
              maxLength={60}
              value={name}
              // Campo aberto por pedido explícito do operador: o foco vai direto para ele.
              // oxlint-disable-next-line jsx-a11y/no-autofocus
              autoFocus
              onChange={(event) => {
                setName(event.target.value);
              }}
            />
          </label>
          {canShare && (
            <label className="flex items-center gap-1.5 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={shared}
                onChange={(event) => {
                  setShared(event.target.checked);
                }}
              />
              Compartilhar com a equipe
            </label>
          )}
          <button type="submit" className={`${buttonPrimary} h-8 py-0 text-xs`} disabled={name.trim() === ''}>
            Salvar
          </button>
          <button
            type="button"
            className="text-xs text-slate-500 hover:text-slate-800"
            onClick={() => {
              setNaming(false);
              setError(null);
            }}
          >
            Cancelar
          </button>
        </form>
      ) : (
        !alreadySaved && (
          <button
            type="button"
            className="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-50 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={() => {
              setNaming(true);
            }}
          >
            <BookmarkPlus aria-hidden="true" className="size-3.5" />
            Salvar esta visão
          </button>
        )
      )}
      {error !== null && (
        <p role="alert" className="basis-full text-xs text-red-700">
          {error}
        </p>
      )}
      {removing !== null && (
        <ConfirmDialog
          title={`Apagar a visão "${removing.name}"?`}
          description={
            removing.shared
              ? 'A visão some para a equipe toda. Os leads não mudam; só o atalho deixa de existir.'
              : 'Os leads não mudam; só o atalho deixa de existir.'
          }
          confirmLabel="Apagar"
          destructive
          onConfirm={() => {
            void deleteView(removing.id).then(() => {
              setRemoving(null);
              onChanged();
            });
          }}
          onCancel={() => {
            setRemoving(null);
          }}
        />
      )}
    </div>
  );
}
