import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime, safeHref } from '../../app/format';
import { PRIORITY_LABEL } from '../../app/labels';
import { Link, navigate } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { PageHeader } from '../../components/PageHeader';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { archiveCompany, getCompany, updateCompany } from '../../services/companyService';
import { createLead } from '../../services/leadService';
import type { Company } from '../../types/company';
import { LEAD_PRIORITIES, type LeadPriority } from '../../types/lead';
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

function ArchiveAction({
  company,
  onArchived,
}: {
  readonly company: Company;
  readonly onArchived: () => void;
}): React.JSX.Element {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function archive(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await archiveCompany(company.id);
      setConfirming(false);
      onArchived();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
      setConfirming(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className={buttonSecondary}
        onClick={() => {
          setConfirming(true);
        }}
      >
        Arquivar
      </button>
      {error !== null && (
        <p role="alert" className="w-full text-sm text-red-700">
          {error}
        </p>
      )}
      {confirming && (
        <ConfirmDialog
          title="Arquivar empresa?"
          description={`${company.name} sai da listagem padrão e deixa de aceitar alterações e novas oportunidades. O histórico é preservado.`}
          confirmLabel="Arquivar"
          destructive
          busy={busy}
          onConfirm={() => {
            void archive();
          }}
          onCancel={() => {
            setConfirming(false);
          }}
        />
      )}
    </>
  );
}

function OpenLeadSection({ companyId }: { readonly companyId: string }): React.JSX.Element {
  const [priority, setPriority] = useState<LeadPriority>('MEDIUM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function open(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const lead = await createLead(companyId, priority);
      navigate(`/leads/${lead.id}`);
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'Esta empresa já tem uma oportunidade aberta.'));
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="oportunidade" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="oportunidade" className="text-base font-semibold">
        Oportunidade
      </h2>
      <p className="mt-1 text-sm text-slate-600">Abre um lead para acompanhar a intenção comercial.</p>
      <form
        className="mt-3 space-y-3"
        onSubmit={(event) => {
          void open(event);
        }}
      >
        <label className="block text-sm font-medium text-slate-700">
          Prioridade
          <select
            className={input}
            value={priority}
            onChange={(event) => {
              const value = LEAD_PRIORITIES.find((item) => item === event.target.value);
              if (value !== undefined) {
                setPriority(value);
              }
            }}
          >
            {LEAD_PRIORITIES.map((item) => (
              <option key={item} value={item}>
                {PRIORITY_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className={buttonPrimary} disabled={busy}>
          {busy ? 'Abrindo…' : 'Abrir lead'}
        </button>
        {error !== null && (
          <p role="alert" className="text-sm text-red-700">
            {error}{' '}
            <Link to="/leads" className={link}>
              Ver leads
            </Link>
          </p>
        )}
      </form>
    </section>
  );
}
