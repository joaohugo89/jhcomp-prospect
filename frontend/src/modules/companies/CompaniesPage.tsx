import { useState } from 'react';

import { SOURCE_TYPE_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLastReady, useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { SegmentSelect } from '../../components/SegmentSelect';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, input } from '../../components/styles';
import { listCompanyWorklist } from '../../services/companyService';
import { getSegments } from '../../services/worklistService';
import { SOURCE_TYPES, type CompanyWorklistFilters } from '../../types/company';
import {
  companyFiltersFromQuery,
  companyFiltersToQuery,
  EMPTY_COMPANY_FILTERS,
  isCompanyFiltered,
} from './companyFilters';
import { CompanyPreview } from './CompanyPreview';
import { CompanyTable } from './CompanyTable';
import { CompanyBulkBar, CompanyQuickFilters } from './CompanyWorklistControls';

/** Grava os filtros na URL sem nova navegação: voltar e recarregar mantêm o recorte. */
function writeUrl(query: string): void {
  window.history.replaceState(null, '', `${window.location.pathname}${query === '' ? '' : `?${query}`}`);
}

/**
 * Empresas como ferramenta de trabalho (RF-012, D32): lead atual, contato,
 * origem, atividade e possível duplicata em cada linha; prévia ao lado,
 * recortes de um clique e correção de segmento ou cidade em lote.
 */
export function CompaniesPage(): React.JSX.Element {
  const [applied, setApplied] = useState<CompanyWorklistFilters>(() => companyFiltersFromQuery(window.location.search));
  const [draft, setDraft] = useState(applied);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [previewId, setPreviewId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const query = companyFiltersToQuery(applied);

  const [state, reload] = useLoad(`${query}|${page}|${version}`, (signal) =>
    listCompanyWorklist(applied, page, signal),
  );
  const shown = useLastReady(state);
  const [segments] = useLoad(`segments|${version}`, (signal) => getSegments(signal));

  function apply(next: CompanyWorklistFilters): void {
    setApplied(next);
    setDraft(next);
    setPage(0);
    setSelected(new Set());
    writeUrl(companyFiltersToQuery(next));
  }

  const filtered = isCompanyFiltered(applied);

  return (
    <>
      <PageHeader
        title="Empresas"
        description="Cadastro mestre. Uma empresa pode existir sem oportunidade aberta."
        actions={
          <Link to="/companies/new" className={buttonPrimary}>
            Nova empresa
          </Link>
        }
      />

      <div className="mb-4">
        <CompanyQuickFilters filters={applied} onApply={apply} />
      </div>

      <form
        role="search"
        aria-label="Filtrar empresas"
        className="mb-4 rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm"
        onSubmit={(event) => {
          event.preventDefault();
          apply(draft);
        }}
      >
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))_auto]">
          <label className="text-sm font-medium text-slate-700">
            Buscar por nome
            <input
              type="search"
              className={input}
              value={draft.search}
              onChange={(event) => {
                setDraft({ ...draft, search: event.target.value });
              }}
            />
          </label>
          <SegmentSelect
            value={{ segment: draft.segment, noSegment: draft.noSegment }}
            onChange={(choice) => {
              setDraft({ ...draft, ...choice });
            }}
            segments={segments.kind === 'ready' ? segments.data : null}
            allowNone
          />
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
          <label className="text-sm font-medium text-slate-700">
            Origem
            <select
              className={input}
              value={draft.source}
              onChange={(event) => {
                setDraft({ ...draft, source: SOURCE_TYPES.find((item) => item === event.target.value) ?? '' });
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
          <div className="flex items-end">
            <button type="submit" className={buttonPrimary}>
              Filtrar
            </button>
          </div>
        </div>
        {filtered && (
          <button
            type="button"
            className="mt-3 text-sm font-medium text-slate-500 hover:text-slate-800"
            onClick={() => {
              apply({ ...EMPTY_COMPANY_FILTERS, sort: applied.sort, direction: applied.direction });
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
          <CompanyBulkBar
            selected={selected}
            segments={segments.kind === 'ready' ? segments.data.segments.map((item) => item.name) : []}
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
      ) : shown.content.length === 0 ? (
        <EmptyState
          title={filtered ? 'Nenhuma empresa corresponde aos filtros.' : 'Nenhuma empresa cadastrada ainda.'}
          {...(filtered
            ? {}
            : {
                action: (
                  <Link to="/companies/new" className={buttonPrimary}>
                    Cadastrar a primeira
                  </Link>
                ),
              })}
        />
      ) : (
        <div className={`transition-opacity ${state.kind === 'loading' ? 'opacity-60' : ''}`}>
          <CompanyTable
            items={shown.content}
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
          <Pagination page={shown} onChange={setPage} />
        </div>
      )}

      {previewId !== null && (
        <CompanyPreview
          key={previewId}
          companyId={previewId}
          onClose={() => {
            setPreviewId(null);
          }}
        />
      )}
    </>
  );
}
