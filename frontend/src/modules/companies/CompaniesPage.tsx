import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { DataTable, type Column } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { listCompanies } from '../../services/companyService';
import type { Company, CompanyFilters } from '../../types/company';

const NO_FILTERS: CompanyFilters = { search: '', segment: '', city: '', includeArchived: false };

const COLUMNS: readonly Column<Company>[] = [
  {
    header: 'Nome',
    render: (company) => (
      <span className="flex flex-wrap items-center gap-2">
        <Link to={`/companies/${company.id}`} className={link}>
          {company.name}
        </Link>
        {company.archived && (
          <span className="rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-700">Arquivada</span>
        )}
      </span>
    ),
  },
  { header: 'Segmento', render: (company) => company.segment ?? '—', hideOnSmall: true },
  { header: 'Cidade', render: (company) => `${company.city}/${company.state}` },
  { header: 'Atualizada em', render: (company) => formatDateTime(company.updatedAt), hideOnSmall: true },
];

/** Lista de empresas (RF-012, doc 08 §6). */
export function CompaniesPage(): React.JSX.Element {
  // Rascunho do formulário e filtros aplicados ficam separados: a busca só
  // dispara ao enviar, e não a cada tecla.
  const [draft, setDraft] = useState<CompanyFilters>(NO_FILTERS);
  const [filters, setFilters] = useState<CompanyFilters>(NO_FILTERS);
  const [page, setPage] = useState(0);

  const [state, reload] = useLoad(JSON.stringify({ filters, page }), (signal) =>
    listCompanies(filters, page, signal),
  );

  const filtered = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS);

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

      <form
        role="search"
        aria-label="Filtrar empresas"
        className="mb-4 grid gap-3 rounded-lg border border-slate-200 bg-white p-4 md:grid-cols-[2fr_1fr_1fr_auto]"
        onSubmit={(event) => {
          event.preventDefault();
          setPage(0);
          setFilters(draft);
        }}
      >
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
        <div className="flex flex-wrap items-end gap-3">
          <label className="flex items-center gap-2 pb-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={draft.includeArchived}
              onChange={(event) => {
                setDraft({ ...draft, includeArchived: event.target.checked });
              }}
            />
            Incluir arquivadas
          </label>
          <button type="submit" className={buttonSecondary}>
            Filtrar
          </button>
        </div>
      </form>

      {state.kind === 'ready' ? (
        state.data.content.length === 0 ? (
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
          <>
            <DataTable caption="Empresas" columns={COLUMNS} rows={state.data.content} rowKey={(row) => row.id} />
            <Pagination page={state.data} onChange={setPage} />
          </>
        )
      ) : (
        <LoadStateView state={state} onRetry={reload} />
      )}
    </>
  );
}
