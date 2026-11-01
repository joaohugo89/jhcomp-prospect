import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { PRIORITY_LABEL, SOURCE_TYPE_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useFilters } from '../../app/useFilters';
import { useLoad } from '../../app/useLoad';
import { PriorityBadge, ScoreIndicator, StatusBadge } from '../../components/Badges';
import { DataTable, type Column } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { listLeads } from '../../services/leadService';
import { SOURCE_TYPES } from '../../types/company';
import { LEAD_PRIORITIES, LEAD_STATUSES, type Lead, type LeadFilters } from '../../types/lead';

const NO_FILTERS: LeadFilters = { search: '', status: '', priority: '', city: '', segment: '', source: '' };

const COLUMNS: readonly Column<Lead>[] = [
  {
    header: 'Empresa',
    render: (lead) => (
      <Link to={`/leads/${lead.id}`} className={link}>
        {lead.company.name}
      </Link>
    ),
  },
  { header: 'Status', render: (lead) => <StatusBadge status={lead.status} /> },
  { header: 'Prioridade', render: (lead) => <PriorityBadge priority={lead.priority} />, hideOnSmall: true },
  { header: 'Score', render: (lead) => <ScoreIndicator score={lead.score} />, hideOnSmall: true },
  { header: 'Segmento', render: (lead) => lead.company.segment ?? '—', hideOnSmall: true },
  { header: 'Cidade', render: (lead) => `${lead.company.city}/${lead.company.state}`, hideOnSmall: true },
  { header: 'Atualizado em', render: (lead) => formatDateTime(lead.updatedAt), hideOnSmall: true },
];

function parseOption<T extends string>(options: readonly T[], value: string): T | '' {
  return options.find((option) => option === value) ?? '';
}

/**
 * Filtros da URL (`/leads?status=WON&source=CSV_IMPORT`): o dashboard abre a
 * lista já filtrada, e a busca pode ser compartilhada ou favoritada. Valor
 * desconhecido é ignorado, como se não viesse.
 */
function filtersFromUrl(): LeadFilters {
  const params = new URLSearchParams(window.location.search);
  const text = (key: string): string => params.get(key)?.trim() ?? '';
  return {
    search: text('search'),
    status: parseOption(LEAD_STATUSES, text('status')),
    priority: parseOption(LEAD_PRIORITIES, text('priority')),
    city: text('city'),
    segment: text('segment'),
    source: parseOption(SOURCE_TYPES, text('source')),
  };
}

/** Grava os filtros aplicados na URL sem nova navegação: voltar e recarregar mantêm a busca. */
function writeUrl(filters: LeadFilters): void {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (typeof value === 'string' && value.trim() !== '') {
      params.set(key, value.trim());
    }
  }
  const query = params.toString();
  window.history.replaceState(null, '', `${window.location.pathname}${query === '' ? '' : `?${query}`}`);
}

/** Lista de leads (RF-012, doc 08 §6). */
export function LeadsPage(): React.JSX.Element {
  const [initial] = useState(filtersFromUrl);
  const { draft, setDraft, applied, page, setPage, apply, isFiltered, queryKey } = useFilters(NO_FILTERS, initial);
  const [state, reload] = useLoad(queryKey, (signal) => listLeads(applied, page, signal));

  return (
    <>
      <PageHeader title="Leads" description="Oportunidades comerciais em acompanhamento." />

      <form
        role="search"
        aria-label="Filtrar leads"
        className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 md:grid-cols-3 xl:grid-cols-[2fr_1fr_1fr_1fr_1fr_1fr_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          apply();
          writeUrl(draft);
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
          Status
          <select
            className={input}
            value={draft.status}
            onChange={(event) => {
              setDraft({ ...draft, status: parseOption(LEAD_STATUSES, event.target.value) });
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
        <label className="text-sm font-medium text-slate-700">
          Prioridade
          <select
            className={input}
            value={draft.priority}
            onChange={(event) => {
              setDraft({ ...draft, priority: parseOption(LEAD_PRIORITIES, event.target.value) });
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
          Origem
          <select
            className={input}
            value={draft.source}
            onChange={(event) => {
              setDraft({ ...draft, source: parseOption(SOURCE_TYPES, event.target.value) });
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
          <button type="submit" className={buttonSecondary}>
            Filtrar
          </button>
        </div>
      </form>

      {state.kind === 'ready' ? (
        state.data.content.length === 0 ? (
          <EmptyState
            title={isFiltered ? 'Nenhum lead corresponde aos filtros.' : 'Nenhum lead ainda.'}
            {...(isFiltered
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
          <>
            <DataTable caption="Leads" columns={COLUMNS} rows={state.data.content} rowKey={(row) => row.id} />
            <Pagination page={state.data} onChange={setPage} />
          </>
        )
      ) : (
        <LoadStateView state={state} onRetry={reload} />
      )}
    </>
  );
}
