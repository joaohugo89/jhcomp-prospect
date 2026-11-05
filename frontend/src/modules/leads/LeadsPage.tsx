import { SlidersHorizontal } from 'lucide-react';
import { useEffect, useState } from 'react';

import { PRIORITY_LABEL, SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLastReady, useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { SegmentSelect } from '../../components/SegmentSelect';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input } from '../../components/styles';
import { getTeam } from '../../services/teamService';
import { getSegments, listViews, listWorklist } from '../../services/worklistService';
import { SOURCE_TYPES } from '../../types/company';
import { LEAD_PRIORITIES, LEAD_STATUSES } from '../../types/lead';
import type { Session } from '../../types/session';
import type { WorklistFilters } from '../../types/worklist';
import { saveNavigation } from './leadNavigation';
import { LeadPreview } from './LeadPreview';
import { BulkBar, QuickFilters, SavedViews } from './WorklistControls';
import { WorklistTable } from './WorklistTable';
import { EMPTY_FILTERS, filtersFromQuery, filtersToQuery, isFiltered } from './worklistFilters';

function pick<T extends string>(options: readonly T[], value: string): T | '' {
  return options.find((option) => option === value) ?? '';
}

/** Grava os filtros na URL sem nova navegação: voltar e recarregar mantêm a busca. */
function writeUrl(query: string): void {
  window.history.replaceState(null, '', `${window.location.pathname}${query === '' ? '' : `?${query}`}`);
}

/**
 * Lista de leads como fila de trabalho (RF-012, doc 08 §6, D31).
 *
 * Cada linha diz o que fazer com o lead; clicar abre o resumo ao lado. Os
 * filtros moram na URL (drill-down do dashboard, favoritos e visões salvas
 * usam a mesma forma), e a ordem da página alimenta o "anterior/próximo" do
 * detalhe.
 */
export function LeadsPage({ session }: { readonly session: Session }): React.JSX.Element {
  const [applied, setApplied] = useState<WorklistFilters>(() => filtersFromQuery(window.location.search));
  const [draft, setDraft] = useState(applied);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  // Filtros secundários: contados no botão e abertos de saída quando a URL já traz algum.
  const advancedCount = [
    applied.priority,
    applied.city.trim(),
    applied.source,
    applied.scoreMin,
    applied.scoreMax,
  ].filter((value) => value !== '').length;
  const [showMore, setShowMore] = useState(advancedCount > 0);
  const query = filtersToQuery(applied);

  const [state, reload] = useLoad(`${query}|${page}|${version}`, async (signal) => ({
    page: await listWorklist(applied, page, signal),
    receivedAt: Date.now(),
  }));
  // Ao trocar filtro ou página, a tabela anterior fica, atenuada, até a nova chegar.
  const shown = useLastReady(state);
  const [segments] = useLoad('segments', (signal) => getSegments(signal));
  const [team] = useLoad('team', (signal) => getTeam(signal));
  const [viewsVersion, setViewsVersion] = useState(0);
  const [views] = useLoad(`views|${viewsVersion}`, (signal) => listViews(signal));

  const ids = shown?.page.content.map((item) => item.leadId).join(',') ?? '';
  useEffect(() => {
    if (ids !== '') {
      saveNavigation({ ids: ids.split(','), query });
    }
  }, [ids, query]);

  function apply(next: WorklistFilters): void {
    setApplied(next);
    setDraft(next);
    setPage(0);
    setSelected(new Set());
    writeUrl(filtersToQuery(next));
  }

  const members = team.kind === 'ready' ? team.data.content : [];
  const filtered = isFiltered(applied);

  return (
    <>
      <PageHeader title="Leads" description="Fila de trabalho: o que fazer com cada oportunidade." />

      <div className="mb-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
        <QuickFilters filters={applied} currentUserId={session.userId} onApply={apply} />
        <SavedViews
          views={views.kind === 'ready' ? views.data : []}
          currentQuery={query}
          canShare={session.role === 'ADMIN'}
          onChanged={() => {
            setViewsVersion((value) => value + 1);
          }}
        />
      </div>

      <form
        role="search"
        aria-label="Filtrar leads"
        className="mb-3 rounded-2xl border border-slate-200/70 bg-white p-3 shadow-sm"
        onSubmit={(event) => {
          event.preventDefault();
          apply(draft);
        }}
      >
        {/* Os quatro filtros mais usados à vista; o resto em "Mais filtros", aberto quando algum está ativo. */}
        <div className="grid grid-cols-2 gap-x-3 gap-y-2 lg:grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))_auto]">
          <label className="col-span-2 text-xs font-medium text-slate-500 lg:col-span-1">
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
          <label className="text-xs font-medium text-slate-500">
            Status
            <select
              className={input}
              value={draft.status}
              onChange={(event) => {
                setDraft({ ...draft, status: pick(LEAD_STATUSES, event.target.value) });
              }}
            >
              <option value="">Todos</option>
              {LEAD_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {STATUS_LABEL[status]}
                </option>
              ))}
            </select>
          </label>
          <SegmentSelect
            value={{ segment: draft.segment, noSegment: draft.noSegment }}
            onChange={(choice) => {
              setDraft({ ...draft, ...choice });
            }}
            segments={segments.kind === 'ready' ? segments.data : null}
            allowNone
          />
          <label className="text-xs font-medium text-slate-500">
            Responsável
            <select
              className={input}
              value={draft.unassigned ? '__nobody__' : draft.assignee}
              onChange={(event) => {
                const value = event.target.value;
                setDraft({
                  ...draft,
                  unassigned: value === '__nobody__',
                  assignee: value === '__nobody__' ? '' : value,
                });
              }}
            >
              <option value="">Todos</option>
              <option value="__nobody__">Sem responsável</option>
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.userId === session.userId ? `${member.displayName} (você)` : member.displayName}
                </option>
              ))}
            </select>
          </label>
          <div className="col-span-2 flex items-end gap-2 lg:col-span-1">
            <button
              type="button"
              aria-expanded={showMore}
              aria-controls="mais-filtros"
              className={`${buttonSecondary} gap-1.5 whitespace-nowrap`}
              onClick={() => {
                setShowMore(!showMore);
              }}
            >
              <SlidersHorizontal aria-hidden="true" className="size-4" />
              Mais filtros
              {advancedCount > 0 && (
                <span className="rounded-full bg-brand-600 px-1.5 text-[11px] font-semibold text-white">
                  {advancedCount}
                  <span className="sr-only">{' ativos'}</span>
                </span>
              )}
            </button>
            <button type="submit" className={buttonPrimary}>
              Filtrar
            </button>
          </div>
        </div>
        {showMore && (
          <div
            id="mais-filtros"
            className="mt-3 grid gap-3 border-t border-slate-100 pt-3 sm:grid-cols-2 lg:grid-cols-4"
          >
            <label className="text-xs font-medium text-slate-500">
              Prioridade
              <select
                className={input}
                value={draft.priority}
                onChange={(event) => {
                  setDraft({ ...draft, priority: pick(LEAD_PRIORITIES, event.target.value) });
                }}
              >
                <option value="">Todas</option>
                {LEAD_PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {PRIORITY_LABEL[priority]}
                  </option>
                ))}
              </select>
            </label>
            <label className="text-xs font-medium text-slate-500">
              Cidade
              <input
                className={input}
                value={draft.city}
                onChange={(event) => {
                  setDraft({ ...draft, city: event.target.value });
                }}
              />
            </label>
            <label className="text-xs font-medium text-slate-500">
              Origem
              <select
                className={input}
                value={draft.source}
                onChange={(event) => {
                  setDraft({ ...draft, source: pick(SOURCE_TYPES, event.target.value) });
                }}
              >
                <option value="">Todas</option>
                {SOURCE_TYPES.map((source) => (
                  <option key={source} value={source}>
                    {SOURCE_TYPE_LABEL[source]}
                  </option>
                ))}
              </select>
            </label>
            <fieldset className="text-xs font-medium text-slate-500">
              <legend>Score</legend>
              <div className="mt-1 flex items-center gap-1.5">
                <label className="flex-1">
                  <span className="sr-only">Score mínimo</span>
                  <input
                    inputMode="numeric"
                    placeholder="de"
                    className={`${input} mt-0`}
                    value={draft.scoreMin}
                    onChange={(event) => {
                      setDraft({ ...draft, scoreMin: event.target.value.replace(/\D/g, '').slice(0, 3) });
                    }}
                  />
                </label>
                <span aria-hidden="true" className="text-slate-400">
                  –
                </span>
                <label className="flex-1">
                  <span className="sr-only">Score máximo</span>
                  <input
                    inputMode="numeric"
                    placeholder="até"
                    className={`${input} mt-0`}
                    value={draft.scoreMax}
                    onChange={(event) => {
                      setDraft({ ...draft, scoreMax: event.target.value.replace(/\D/g, '').slice(0, 3) });
                    }}
                  />
                </label>
              </div>
            </fieldset>
          </div>
        )}
        {filtered && (
          <button
            type="button"
            className="mt-3 text-sm font-medium text-slate-500 hover:text-slate-800"
            onClick={() => {
              apply({ ...EMPTY_FILTERS, sort: applied.sort, direction: applied.direction });
            }}
          >
            Limpar filtros
          </button>
        )}
      </form>

      <div aria-live="polite">
        {notice !== null && (
          <p role="status" className="mb-3 rounded-xl bg-emerald-50 px-4 py-2 text-sm text-emerald-800">
            {notice}
          </p>
        )}
      </div>

      {selected.size > 0 && (
        <div className="sticky top-2 z-30 mb-3">
          <BulkBar
            selected={selected}
            team={members}
            onDone={(message) => {
              setNotice(message);
              setSelected(new Set());
              setVersion((value) => value + 1);
            }}
            onClear={() => {
              setSelected(new Set());
            }}
          />
        </div>
      )}

      {shown === null || (state.kind !== 'ready' && state.kind !== 'loading') ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : shown.page.content.length === 0 ? (
        <EmptyState
          title={filtered ? 'Nenhum lead corresponde aos filtros.' : 'Nenhum lead ainda.'}
          {...(filtered
            ? {}
            : {
                description: 'Leads são abertos a partir de uma empresa cadastrada.',
                action: (
                  <Link to="/companies" className={buttonPrimary}>
                    Ir para empresas
                  </Link>
                ),
              })}
        />
      ) : (
        <div className={`transition-opacity ${state.kind === 'loading' ? 'opacity-60' : ''}`}>
          <WorklistTable
            items={shown.page.content}
            now={shown.receivedAt}
            sort={applied.sort}
            direction={applied.direction}
            onSort={(sort, direction) => {
              apply({ ...applied, sort, direction });
            }}
            selected={selected}
            onSelect={setSelected}
            previewId={previewId}
            onPreview={setPreviewId}
          />
          <Pagination page={shown.page} onChange={setPage} />
        </div>
      )}

      {previewId !== null && (
        <LeadPreview
          key={previewId}
          leadId={previewId}
          now={shown?.receivedAt ?? 0}
          onClose={() => {
            setPreviewId(null);
          }}
        />
      )}
    </>
  );
}
