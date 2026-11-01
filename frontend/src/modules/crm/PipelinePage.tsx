import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatCurrency } from '../../app/format';
import { usePreference } from '../../app/preferences';
import { Link } from '../../app/router';
import { useFilters } from '../../app/useFilters';
import { useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input } from '../../components/styles';
import { getBoard, type BoardFilters } from '../../services/crmService';
import { changeLeadStatus } from '../../services/leadService';
import type { BoardCard, BoardColumn } from '../../types/crm';
import { LEAD_STATUSES, type LeadStatus } from '../../types/lead';
import { IRREVERSIBLE } from '../leads/StatusChangeForm';
import type { Density, Moving } from './PipelineCard';
import { PipelineColumn } from './PipelineColumn';

const NO_FILTERS: BoardFilters = { search: '', segment: '', city: '' };

/** Escolha de quem usa, por etapa; sem escolha, etapa vazia começa recolhida. */
type CollapsedStages = Readonly<Partial<Record<LeadStatus, boolean>>>;

function parseDensity(raw: unknown): Density | undefined {
  return raw === 'compact' || raw === 'detailed' ? raw : undefined;
}

function parseCollapsed(raw: unknown): CollapsedStages | undefined {
  if (typeof raw !== 'object' || raw === null) {
    return undefined;
  }
  const entries = Object.entries(raw).filter(
    (entry): entry is [LeadStatus, boolean] =>
      LEAD_STATUSES.some((status) => status === entry[0]) && typeof entry[1] === 'boolean',
  );
  return Object.fromEntries(entries);
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
  const [density, setDensity] = usePreference<Density>('pipeline.density', 'compact', parseDensity);
  const [collapsedStages, setCollapsedStages] = usePreference<CollapsedStages>(
    'pipeline.collapsed',
    {},
    parseCollapsed,
  );
  // "Agora" dos sinais: fixo enquanto a tela está aberta, para todos os cards concordarem.
  const [now] = useState(() => Date.now());

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
      <PageHeader
        title="Pipeline"
        description="Arraste um card ou use “Mover” para mudar o status do lead."
        actions={<DensityToggle density={density} onChange={setDensity} />}
      />

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
        <>
          <BoardSummary columns={state.data} />
          {/* A partir do tablet o quadro ocupa o resto da tela (AppShell, FULL_SCREEN):
              as colunas dividem a largura e cada uma rola por dentro. */}
          <div className="-mx-4 overflow-x-auto px-4 pb-4 md:min-h-0 md:flex-1 md:pb-2">
            <div className="flex gap-3 md:h-full">
              {state.data.map((column) => (
                <PipelineColumn
                  key={column.stage.key}
                  column={column}
                  density={density}
                  now={now}
                  collapsed={collapsedStages[column.stage.key] ?? column.total === 0}
                  onToggle={() => {
                    const current = collapsedStages[column.stage.key] ?? column.total === 0;
                    setCollapsedStages({ ...collapsedStages, [column.stage.key]: !current });
                  }}
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
        </>
      )}
    </>
  );
}

/** Totais do quadro: o que está em aberto e o que foi ganho na janela das colunas finais. */
function BoardSummary({ columns }: { readonly columns: readonly BoardColumn[] }): React.JSX.Element {
  const open = columns.filter((column) => !column.stage.terminal);
  const won = columns.find((column) => column.stage.key === 'WON');
  const openLeads = open.reduce((sum, column) => sum + column.total, 0);
  const openValue = open.reduce((sum, column) => sum + column.totalValue, 0);

  return (
    <dl aria-label="Resumo do quadro" className="mb-3 flex flex-wrap gap-x-6 gap-y-1 text-sm">
      <div className="flex items-baseline gap-1.5">
        <dt className="text-slate-500">Em aberto</dt>
        <dd className="font-semibold text-slate-900 tabular-nums">
          {`${openLeads} ${openLeads === 1 ? 'lead' : 'leads'} · ${formatCurrency(openValue)}`}
        </dd>
      </div>
      {won?.recentDays !== undefined && (
        <div className="flex items-baseline gap-1.5">
          <dt className="text-slate-500">{`Ganho nos últimos ${won.recentDays} dias`}</dt>
          <dd className="font-semibold text-emerald-700 tabular-nums">
            {`${won.total} · ${formatCurrency(won.totalValue)}`}
          </dd>
        </div>
      )}
    </dl>
  );
}

/** Compacto mostra mais leads por tela; detalhado traz tudo o que o card sabe. */
function DensityToggle({
  density,
  onChange,
}: {
  readonly density: Density;
  readonly onChange: (density: Density) => void;
}): React.JSX.Element {
  const options: readonly [Density, string][] = [
    ['compact', 'Compacto'],
    ['detailed', 'Detalhado'],
  ];
  return (
    <div role="group" aria-label="Densidade dos cards" className="inline-flex rounded-lg bg-slate-200/70 p-0.5">
      {options.map(([value, label]) => (
        <button
          key={value}
          type="button"
          aria-pressed={density === value}
          className={`rounded-md px-3 py-1.5 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-brand-500 ${
            density === value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'
          }`}
          onClick={() => {
            onChange(value);
          }}
        >
          {label}
        </button>
      ))}
    </div>
  );
}
