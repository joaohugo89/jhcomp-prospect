import { useState } from 'react';

import { formatDateTime, safeHref } from '../../app/format';
import { useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { LoadStateView } from '../../components/States';
import { buttonSecondary, link } from '../../components/styles';
import { getCompany, updateCompany } from '../../services/companyService';
import type { Company } from '../../types/company';
import { ArchiveAction, OpenLeadSection } from './CompanyActions';
import { CompanyForm, toInput } from './CompanyForm';
import { DuplicatesSection } from './DuplicatesSection';
import { SourcesSection } from './SourcesSection';

/** Detalhe da empresa (RF-010, RF-014, RF-022). */
export function CompanyDetailPage({ id }: { readonly id: string }): React.JSX.Element {
  const [state, reload] = useLoad(id, (signal) => getCompany(id, signal));
  const [editing, setEditing] = useState(false);

  if (state.kind !== 'ready') {
    return <LoadStateView state={state} onRetry={reload} notFoundMessage="Empresa não encontrada." />;
  }

  const company = state.data;

  if (editing) {
    return (
      <>
        <PageHeader title={`Editar ${company.name}`} />
        <CompanyForm
          initial={toInput(company)}
          withSource={false}
          submitLabel="Salvar alterações"
          onSubmit={async (values) => {
            await updateCompany(company.id, values);
            setEditing(false);
            reload();
          }}
          onCancel={() => {
            setEditing(false);
          }}
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={company.name}
        description={
          <>
            {[company.segment, `${company.city}/${company.state}`].filter((part) => part !== undefined).join(' · ')}
            {company.archived && (
              <span className="ml-2 rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-700">
                Arquivada em {company.archivedAt === undefined ? '—' : formatDateTime(company.archivedAt)}
              </span>
            )}
          </>
        }
        actions={
          company.archived ? undefined : (
            <>
              <button
                type="button"
                className={buttonSecondary}
                onClick={() => {
                  setEditing(true);
                }}
              >
                Editar
              </button>
              <ArchiveAction company={company} onArchived={reload} />
            </>
          )
        }
      />

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <div className="space-y-6">
          <CompanyData company={company} />
          <SourcesSection companyId={company.id} />
        </div>
        <div className="space-y-6">
          {!company.archived && <OpenLeadSection companyId={company.id} />}
          <DuplicatesSection companyId={company.id} />
        </div>
      </div>
    </>
  );
}

function CompanyData({ company }: { readonly company: Company }): React.JSX.Element {
  const website = safeHref(company.websiteUrl);
  const instagram = safeHref(company.instagramUrl);

  const rows: readonly [string, React.ReactNode][] = [
    ['Nome comercial', company.tradeName],
    [
      'Site',
      website === undefined ? undefined : (
        <a key="site" href={website} target="_blank" rel="noopener noreferrer" className={`${link} break-all`}>
          {company.websiteUrl}
        </a>
      ),
    ],
    [
      'Instagram',
      instagram === undefined ? undefined : (
        <a key="instagram" href={instagram} target="_blank" rel="noopener noreferrer" className={`${link} break-all`}>
          {company.instagramUrl}
        </a>
      ),
    ],
    ['Telefone', company.phone],
    ['E-mail', company.businessEmail],
    ['Endereço', company.address],
    ['Observações', company.notes],
    ['Cadastrada em', formatDateTime(company.createdAt)],
    ['Atualizada em', formatDateTime(company.updatedAt)],
  ];

  return (
    <section aria-labelledby="dados" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="dados" className="text-base font-semibold">
        Dados
      </h2>
      <dl className="mt-3 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-slate-500">{label}</dt>
            {/* Ausente não é "não existe": só não foi informado (doc 06 §5). */}
            <dd className="break-words text-slate-900">{value ?? <span className="text-slate-400">Não informado</span>}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
