import { ArrowLeft, Copy, Pencil } from 'lucide-react';
import { useState } from 'react';

import { formatDateTime, safeHref } from '../../app/format';
import { SOURCE_TYPE_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLastReady, useLoad } from '../../app/useLoad';
import { StatusBadge } from '../../components/Badges';
import { PageHeader } from '../../components/PageHeader';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, card, link } from '../../components/styles';
import { getCompany, getCompanySummary, updateCompany } from '../../services/companyService';
import type { Company, CompanyWorklistItem } from '../../types/company';
import { ArchiveAction, OpenLeadSection } from './CompanyActions';
import { CompanyForm, toInput } from './CompanyForm';
import { ContactIcons } from './CompanyTable';
import { DeleteCompanyAction } from './DeleteCompanyAction';
import { DuplicatesSection } from './DuplicatesSection';
import { LeadCyclesSection } from './LeadCyclesSection';
import { SourcesSection } from './SourcesSection';

/** Nome dos campos que a mesclagem completou, para o aviso. */
const FIELD_LABEL: Readonly<Record<string, string>> = {
  tradeName: 'nome comercial',
  segment: 'segmento',
  websiteUrl: 'site',
  instagramUrl: 'Instagram',
  phone: 'telefone',
  businessEmail: 'e-mail',
  address: 'endereço',
  notes: 'observações',
};

/**
 * Detalhe da empresa (RF-010, RF-014, RF-022, D32), no padrão do detalhe do
 * lead: o essencial no topo (lead atual, contato, origem, atividade) com as
 * ações; dados e ciclos comerciais no centro; oportunidade e duplicatas ao lado.
 */
export function CompanyDetailPage({ id }: { readonly id: string }): React.JSX.Element {
  const [version, setVersion] = useState(0);
  const [state, reload] = useLoad(`${id}|${version}`, (signal) => getCompany(id, signal));
  const [summary] = useLoad(`summary|${id}|${version}`, (signal) => getCompanySummary(id, signal));
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const refresh = (): void => {
    setVersion((value) => value + 1);
  };

  // Depois de editar ou mesclar, a tela anterior fica até a nova chegar, sem piscar.
  const shown = useLastReady(state);
  if (shown === null || (state.kind !== 'ready' && state.kind !== 'loading')) {
    return <LoadStateView state={state} onRetry={reload} notFoundMessage="Empresa não encontrada." />;
  }

  const company = shown;
  const item = summary.kind === 'ready' ? summary.data : null;
  const openLead = item?.currentLead?.open === true ? item.currentLead : undefined;

  if (editing) {
    return (
      <>
        <PageHeader title={`Editar ${company.name}`} />
        <CompanyForm
          initial={toInput(company)}
          withSource={false}
          submitLabel="Salvar alterações"
          companyId={company.id}
          onSubmit={async (values) => {
            await updateCompany(company.id, values);
            setEditing(false);
            refresh();
          }}
          onCancel={() => {
            setEditing(false);
          }}
        />
      </>
    );
  }

  return (
    <div className="space-y-5">
      <Link
        to="/companies"
        className="inline-flex items-center gap-1 text-sm font-medium text-stone-600 hover:text-stone-900"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Voltar para empresas
      </Link>

      <header className="space-y-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="font-display text-2xl font-semibold tracking-tight break-words text-stone-900">
              {company.name}
            </h1>
            <p className="mt-1 text-sm text-stone-500">
              {[company.segment ?? 'Sem segmento', `${company.city}/${company.state}`].join(' · ')}
              {company.archived && (
                <span className="ml-2 rounded bg-stone-200 px-1.5 py-0.5 text-xs text-stone-700">
                  {`Arquivada em ${company.archivedAt === undefined ? '—' : formatDateTime(company.archivedAt)}`}
                </span>
              )}
            </p>
          </div>
          <div role="group" aria-label="Ações da empresa" className="flex flex-wrap gap-2">
            {openLead !== undefined && (
              <Link to={`/leads/${openLead.leadId}`} className={buttonPrimary}>
                Ver lead atual
              </Link>
            )}
            {!company.archived && (
              <>
                <button
                  type="button"
                  className={`${buttonSecondary} gap-1.5`}
                  onClick={() => {
                    setEditing(true);
                  }}
                >
                  <Pencil aria-hidden="true" className="size-4" />
                  Editar
                </button>
                <ArchiveAction company={company} onArchived={refresh} />
              </>
            )}
            <DeleteCompanyAction company={company} onArchived={refresh} />
          </div>
        </div>
        <SummaryStrip item={item} />
      </header>

      <div aria-live="polite">
        {notice !== null && (
          <p role="status" className="rounded-xl bg-emerald-50 px-4 py-2 text-sm text-emerald-800">
            {notice}
          </p>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-4">
          <CompanyData company={company} />
          <LeadCyclesSection companyId={company.id} refreshKey={String(version)} />
          <SourcesSection key={version} companyId={company.id} />
        </div>
        <aside aria-label="Oportunidade e duplicatas" className="space-y-4 lg:sticky lg:top-4 lg:self-start">
          {!company.archived && item !== null && openLead === undefined && <OpenLeadSection companyId={company.id} />}
          <DuplicatesSection
            company={company}
            onMerged={(result, sourceName) => {
              const filled = result.filledFields.map((field) => FIELD_LABEL[field] ?? field);
              setNotice(
                `${sourceName} foi mesclada e arquivada.${
                  filled.length === 0 ? '' : ` Completado: ${filled.join(', ')}.`
                }${result.movedLeads === 0 ? '' : ` ${result.movedLeads} lead(s) agora estão aqui.`}`,
              );
              refresh();
            }}
          />
        </aside>
      </div>
    </div>
  );
}

/** O essencial da empresa num relance. */
function SummaryStrip({ item }: { readonly item: CompanyWorklistItem | null }): React.JSX.Element {
  const lead = item?.currentLead;
  return (
    <dl aria-label="Resumo da empresa" className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
      <Tile label="Lead atual" muted={lead === undefined}>
        {lead === undefined ? (
          'Sem lead'
        ) : (
          <span className="flex items-center gap-1.5">
            <StatusBadge status={lead.status} />
            {!lead.open && <span className="text-xs font-normal text-stone-500">encerrado</span>}
          </span>
        )}
      </Tile>
      <Tile label="Responsável" muted={lead?.assigneeName === undefined}>
        {lead?.open === true ? (lead.assigneeName ?? 'Sem responsável') : '—'}
      </Tile>
      <Tile label="Contato">{item === null ? '—' : <ContactIcons item={item} />}</Tile>
      <Tile label="Origem" muted={item?.origin === undefined}>
        {item?.origin === undefined ? '—' : SOURCE_TYPE_LABEL[item.origin]}
      </Tile>
      <Tile label="Última atividade" muted={item?.lastActivityAt === undefined}>
        {item?.lastActivityAt === undefined ? 'Nenhuma' : formatDateTime(item.lastActivityAt)}
      </Tile>
      {item?.possibleDuplicate === true && (
        <div className="col-span-2 flex items-center gap-1.5 rounded-2xl bg-amber-50 px-4 py-2.5 text-sm text-amber-900 ring-1 ring-amber-200 sm:col-span-3 xl:col-span-5">
          <Copy aria-hidden="true" className="size-4 shrink-0" />
          Parece duplicata de outra empresa ativa. Veja em “Possíveis duplicatas” e mescle se for a mesma.
        </div>
      )}
    </dl>
  );
}

function Tile({
  label,
  children,
  muted = false,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
  readonly muted?: boolean;
}): React.JSX.Element {
  return (
    <div className={`${card} min-w-0 px-4 py-3`}>
      <dt className="text-[11px] font-medium text-stone-500">{label}</dt>
      <dd className={`mt-1 truncate text-sm font-semibold ${muted ? 'font-normal text-stone-400' : 'text-stone-900'}`}>
        {children}
      </dd>
    </div>
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
  const missing = rows.filter(([, value]) => value === undefined).map(([label]) => label);

  return (
    <section aria-labelledby="dados" className={`${card} p-4`}>
      <h2 id="dados" className="text-sm font-semibold text-stone-900">
        Dados
      </h2>
      <dl className="mt-3 grid gap-x-6 gap-y-1.5 text-sm sm:grid-cols-[9rem_1fr]">
        {rows
          .filter(([, value]) => value !== undefined)
          .map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-stone-500">{label}</dt>
              <dd className="break-words text-stone-900">{value}</dd>
            </div>
          ))}
      </dl>
      {/* Ausente não é "não existe": só não foi informado (doc 06 §5). Uma linha só para todos. */}
      {missing.length > 0 && (
        <p className="mt-3 border-t border-stone-100 pt-2 text-xs text-stone-500">
          {`Não informado: ${missing.join(', ')}.`}
        </p>
      )}
    </section>
  );
}
