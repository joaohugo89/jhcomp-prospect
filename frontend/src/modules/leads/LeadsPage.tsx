import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { PRIORITY_LABEL, STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { PriorityBadge, ScoreIndicator, StatusBadge } from '../../components/Badges';
import { DataTable, type Column } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { listLeads } from '../../services/leadService';
import { LEAD_PRIORITIES, LEAD_STATUSES, type Lead, type LeadFilters } from '../../types/lead';

const NO_FILTERS: LeadFilters = { search: '', status: '', priority: '', city: '' };

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

/** Lista de leads (RF-012, doc 08 §6). */
export function LeadsPage(): React.JSX.Element {
  const [draft, setDraft] = useState<LeadFilters>(NO_FILTERS);
  const [filters, setFilters] = useState<LeadFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);

  const [state, reload] = useLoad(JSON.stringify({ filters, page }), (signal) => listLeads(filters, page, signal));

  const filtered = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS);

  return (
    <>
      <PageHeader title="Leads" description="Oportunidades comerciais em acompanhamento." />

      <form
        role="search"
        aria-label="Filtrar leads"
        className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 md:grid-cols-[2fr_1fr_1fr_1fr_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setFilters(draft);
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
        <div className="flex items-end">
          <button type="submit" className={buttonSecondary}>
            Filtrar
          </button>
        </div>
      </form>

      {state.kind === 'ready' ? (
        state.data.content.length === 0 ? (
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
