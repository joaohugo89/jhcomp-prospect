import { useState } from 'react';

import { actionErrorMessage, fieldMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import {
  DUPLICATE_SIGNAL_LABEL,
  IMPORT_FIELD_LABEL,
  IMPORT_ROW_STATUS_LABEL,
  IMPORT_STATUS_LABEL,
} from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, link } from '../../components/styles';
import { confirmImport, discardImport, getImport, getImportReport } from '../../services/importService';
import {
  IMPORT_FIELDS,
  type DuplicateChoice,
  type ImportBatch,
  type ImportField,
  type ImportRow,
  type ImportRowStatus,
} from '../../types/import';

/** Revisão e resultado de uma importação (RF-015, D23). */
export function ImportDetailPage({ id }: { readonly id: string }): React.JSX.Element {
  const [state, reload] = useLoad(id, (signal) => getImport(id, signal));
  // Escolha do operador por possível duplicata. Mantida entre páginas do relatório.
  const [choices, setChoices] = useState<ReadonlyMap<number, DuplicateChoice>>(new Map());

  if (state.kind !== 'ready') {
    return <LoadStateView state={state} onRetry={reload} notFoundMessage="Importação não encontrada." />;
  }

  const batch = state.data;
  const reviewing = batch.status === 'ANALYZED';

  return (
    <>
      <PageHeader
        title={batch.fileName}
        description={`${IMPORT_STATUS_LABEL[batch.status]} · origem: ${batch.sourceName} · enviada em ${formatDateTime(batch.createdAt)}`}
      />

      {batch.previousImportId !== undefined && (
        <p role="status" className="mb-4 rounded-md bg-amber-50 px-4 py-3 text-sm text-amber-950">
          Este mesmo arquivo já foi enviado antes.{' '}
          <Link to={`/imports/${batch.previousImportId}`} className={link}>
            Ver importação anterior
          </Link>
          . As linhas já importadas aparecem como possíveis duplicatas.
        </p>
      )}

      <Summary batch={batch} />
      <MappingSummary batch={batch} />

      {reviewing && (
        <ReviewActions
          batch={batch}
          choices={choices}
          onDone={() => {
            setChoices(new Map());
            reload();
          }}
        />
      )}
      {batch.status === 'DISCARDED' && (
        <p className="mb-6 rounded-md bg-slate-100 px-4 py-3 text-sm text-slate-700">
          Importação descartada. Os dados das linhas foram apagados; ficam só os totais.
        </p>
      )}

      {batch.status !== 'DISCARDED' && (
        <Report
          importId={batch.id}
          version={`${batch.status}:${batch.importedRows}:${batch.mergedRows}`}
          reviewing={reviewing}
          choices={choices}
          onChoose={(rowNumber, choice) => {
            setChoices((current) => {
              const next = new Map(current);
              if (choice === null) {
                next.delete(rowNumber);
              } else {
                next.set(rowNumber, choice);
              }
              return next;
            });
          }}
        />
      )}
    </>
  );
}

function Summary({ batch }: { readonly batch: ImportBatch }): React.JSX.Element {
  const items: (readonly [string, number])[] = [
    ['Lidas', batch.totalRows],
    ['Válidas', batch.validRows],
    ['Rejeitadas', batch.rejectedRows],
    ['Possíveis duplicatas', batch.duplicateRows],
  ];
  if (batch.status === 'COMPLETED') {
    items.push(
      ['Importadas', batch.importedRows],
      ['Completadas', batch.mergedRows],
      ['Leads abertos', batch.leadsCreated],
    );
  }

  return (
    <dl className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-7">
      {items.map(([label, value]) => (
        <div key={label} className="rounded-lg border border-slate-200 bg-white px-4 py-3">
          <dt className="text-xs text-slate-500">{label}</dt>
          <dd className="text-xl font-semibold tabular-nums text-slate-900">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function MappingSummary({ batch }: { readonly batch: ImportBatch }): React.JSX.Element {
  const mapped = IMPORT_FIELDS.filter((field) => batch.columnMapping[field] !== undefined);
  const ignored = batch.ignoredColumns ?? [];

  return (
    <section aria-labelledby="colunas" className="mb-6 rounded-lg border border-slate-200 bg-white p-6 text-sm">
      <h2 id="colunas" className="text-base font-semibold">
        Colunas usadas
      </h2>
      <ul className="mt-2 flex flex-wrap gap-2">
        {mapped.map((field) => (
          <li key={field} className="rounded bg-slate-100 px-2 py-1 text-slate-800">
            {IMPORT_FIELD_LABEL[field]} ← <span className="font-mono text-xs">{batch.columnMapping[field]}</span>
          </li>
        ))}
      </ul>
      {ignored.length > 0 && (
        <p className="mt-3 text-slate-600">
          Ignoradas, sem leitura dos valores: <span className="font-mono text-xs">{ignored.join(', ')}</span>
        </p>
      )}
    </section>
  );
}

function ReviewActions({
  batch,
  choices,
  onDone,
}: {
  readonly batch: ImportBatch;
  readonly choices: ReadonlyMap<number, DuplicateChoice>;
  readonly onDone: () => void;
}): React.JSX.Element {
  const [createLeads, setCreateLeads] = useState(false);
  const [dialog, setDialog] = useState<'confirm' | 'discard' | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const rowsWith = (choice: DuplicateChoice): number[] =>
    [...choices].filter(([, value]) => value === choice).map(([row]) => row).toSorted((a, b) => a - b);
  const asNew = rowsWith('new');
  const toMerge = rowsWith('merge');
  const toCreate = batch.validRows + asNew.length;
  const summary = describeOutcome(toCreate, toMerge.length);

  async function run(action: 'confirm' | 'discard'): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      if (action === 'confirm') {
        await confirmImport(batch.id, asNew, toMerge, createLeads);
      } else {
        await discardImport(batch.id);
      }
      setDialog(null);
      onDone();
    } catch (cause: unknown) {
      setDialog(null);
      setError(actionErrorMessage(cause, 'Esta importação já foi processada. Recarregue a página.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="revisao" className="mb-6 rounded-lg border border-brand-500/30 bg-brand-50 p-6">
      <h2 id="revisao" className="text-base font-semibold">
        Revisar e importar
      </h2>
      <p className="mt-1 text-sm text-slate-700">
        Serão importadas as linhas válidas e as possíveis duplicatas em que você escolher o que fazer. Completar uma
        empresa existente só preenche os campos vazios dela; nada que já está cadastrado é alterado. Rejeitadas nunca
        são importadas.
      </p>
      <label className="mt-3 flex items-center gap-2 text-sm text-slate-800">
        <input
          type="checkbox"
          checked={createLeads}
          onChange={(event) => {
            setCreateLeads(event.target.checked);
          }}
        />
        Abrir um lead para cada empresa, se ela ainda não tiver oportunidade aberta
      </label>
      <div className="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          className={buttonPrimary}
          disabled={toCreate + toMerge.length === 0 || busy}
          onClick={() => {
            setDialog('confirm');
          }}
        >
          Confirmar: {summary}
        </button>
        <button
          type="button"
          className={buttonSecondary}
          disabled={busy}
          onClick={() => {
            setDialog('discard');
          }}
        >
          Descartar
        </button>
      </div>
      {error !== null && (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {dialog === 'confirm' && (
        <ConfirmDialog
          title="Confirmar a importação?"
          description={`Resultado: ${summary}. A origem registrada será "${batch.sourceName}"${createLeads ? ', com um lead novo para cada empresa sem oportunidade aberta' : ''}. A importação não pode ser repetida a partir desta tela.`}
          confirmLabel="Importar"
          busy={busy}
          onConfirm={() => {
            void run('confirm');
          }}
          onCancel={() => {
            setDialog(null);
          }}
        />
      )}
      {dialog === 'discard' && (
        <ConfirmDialog
          title="Descartar a importação?"
          description="Nada será importado e os dados das linhas serão apagados. Para importar depois, envie o arquivo de novo."
          confirmLabel="Descartar"
          destructive
          busy={busy}
          onConfirm={() => {
            void run('discard');
          }}
          onCancel={() => {
            setDialog(null);
          }}
        />
      )}
    </section>
  );
}

function describeOutcome(toCreate: number, toMerge: number): string {
  const created = `${toCreate} ${toCreate === 1 ? 'empresa nova' : 'empresas novas'}`;
  if (toMerge === 0) {
    return created;
  }
  return `${created} e ${toMerge} ${toMerge === 1 ? 'completada' : 'completadas'}`;
}

const FILTERS: readonly [ImportRowStatus | '', string][] = [
  ['', 'Todas'],
  ['VALID', 'Válidas'],
  ['DUPLICATE', 'Possíveis duplicatas'],
  ['REJECTED', 'Rejeitadas'],
  ['IMPORTED', 'Importadas'],
  ['MERGED', 'Completaram empresa'],
  ['SKIPPED', 'Não importadas'],
];

function Report({
  importId,
  version,
  reviewing,
  choices,
  onChoose,
}: {
  readonly importId: string;
  readonly version: string;
  readonly reviewing: boolean;
  readonly choices: ReadonlyMap<number, DuplicateChoice>;
  readonly onChoose: (rowNumber: number, choice: DuplicateChoice | null) => void;
}): React.JSX.Element {
  const [filter, setFilter] = useState<ImportRowStatus | ''>('');
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${importId}:${filter}:${page}:${version}`, (signal) =>
    getImportReport(importId, filter, page, signal),
  );

  const filters = FILTERS.filter(([status]) =>
    reviewing ? status !== 'IMPORTED' && status !== 'MERGED' && status !== 'SKIPPED' : status !== 'VALID',
  );

  return (
    <section aria-labelledby="relatorio">
      <h2 id="relatorio" className="mb-3 text-base font-semibold">
        Linhas do arquivo
      </h2>
      <fieldset className="mb-3 flex flex-wrap gap-2">
        <legend className="sr-only">Filtrar linhas</legend>
        {filters.map(([status, label]) => (
          <button
            key={label}
            type="button"
            aria-pressed={filter === status}
            className={`rounded-full px-3 py-1 text-sm ring-1 ring-inset focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
              filter === status ? 'bg-brand-600 text-white ring-brand-600' : 'bg-white text-slate-700 ring-slate-300'
            }`}
            onClick={() => {
              setFilter(status);
              setPage(0);
            }}
          >
            {label}
          </button>
        ))}
      </fieldset>

      {state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : state.data.content.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-300 bg-white px-6 py-6 text-center text-sm text-slate-600">
          Nenhuma linha nesta situação.
        </p>
      ) : (
        <>
          <ol className="space-y-2">
            {state.data.content.map((row) => (
              <li key={row.rowNumber}>
                <RowCard
                  row={row}
                  reviewing={reviewing}
                  choice={choices.get(row.rowNumber) ?? null}
                  onChoose={onChoose}
                />
              </li>
            ))}
          </ol>
          <Pagination page={state.data} onChange={setPage} />
        </>
      )}
    </section>
  );
}

/** Rótulo do campo de um erro; "row" é a linha inteira. */
function errorFieldLabel(field: string): string {
  if (field === 'row') {
    return 'Linha';
  }
  const known = IMPORT_FIELDS.find((item) => item === field);
  return known === undefined ? field : IMPORT_FIELD_LABEL[known];
}

const ROW_TONE: Readonly<Record<ImportRowStatus, string>> = {
  VALID: 'border-emerald-200',
  IMPORTED: 'border-emerald-200',
  MERGED: 'border-sky-300',
  REJECTED: 'border-red-200',
  DUPLICATE: 'border-amber-300',
  SKIPPED: 'border-slate-200',
};

function RowCard({
  row,
  reviewing,
  choice,
  onChoose,
}: {
  readonly row: ImportRow;
  readonly reviewing: boolean;
  readonly choice: DuplicateChoice | null;
  readonly onChoose: (rowNumber: number, choice: DuplicateChoice | null) => void;
}): React.JSX.Element {
  const shown = IMPORT_FIELDS.filter((field) => row.values[field] !== undefined);

  return (
    <article
      aria-label={`Linha ${row.rowNumber}`}
      className={`rounded-lg border-l-4 border-y border-r border-slate-200 bg-white px-4 py-3 text-sm ${ROW_TONE[row.status]}`}
    >
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="font-medium text-slate-900">
          Linha {row.rowNumber}: {row.values.name ?? <span className="text-slate-400">sem nome</span>}
        </p>
        <span className="text-xs font-medium text-slate-600">{IMPORT_ROW_STATUS_LABEL[row.status]}</span>
      </header>

      {shown.length > 0 && (
        <p className="mt-1 text-slate-600">
          {shown
            .filter((field) => field !== 'name')
            .map((field) => `${IMPORT_FIELD_LABEL[field]}: ${row.values[field] ?? ''}`)
            .join(' · ')}
        </p>
      )}

      {row.errors.length > 0 && (
        <ul className="mt-2 list-disc pl-5 text-red-800">
          {row.errors.map((error) => (
            <li key={`${error.field}-${error.message}`}>
              {`${errorFieldLabel(error.field)}: ${fieldMessage(error.message)}`}
            </li>
          ))}
        </ul>
      )}

      {row.duplicateSignals.length > 0 && (
        <p className="mt-2 text-amber-950">
          {row.duplicateSignals.map((signal) => DUPLICATE_SIGNAL_LABEL[signal]).join(' e ')}{' '}
          {row.duplicateOfCompanyId !== undefined ? (
            <>
              de uma{' '}
              <Link to={`/companies/${row.duplicateOfCompanyId}`} className={link}>
                empresa já cadastrada
              </Link>
            </>
          ) : (
            `da linha ${row.duplicateOfRowNumber ?? '?'} deste arquivo`
          )}
          .
        </p>
      )}

      {reviewing && row.status === 'DUPLICATE' && (
        <DuplicateChoiceControl row={row} choice={choice} onChoose={onChoose} />
      )}

      {row.mergedFields !== undefined && (
        <p className="mt-2 text-sky-950">
          {row.mergedFields.length === 0
            ? 'A empresa já tinha todos os campos preenchidos; só a origem foi registrada.'
            : `Campos preenchidos: ${row.mergedFields.map((field: ImportField) => IMPORT_FIELD_LABEL[field]).join(', ')}.`}
        </p>
      )}

      {row.companyId !== undefined && (
        <p className="mt-2">
          <Link to={`/companies/${row.companyId}`} className={link}>
            Ver empresa
          </Link>
          {row.leadId !== undefined && (
            <>
              {' · '}
              <Link to={`/leads/${row.leadId}`} className={link}>
                Ver lead
              </Link>
            </>
          )}
        </p>
      )}
    </article>
  );
}

/**
 * O que fazer com uma possível duplicata.
 *
 * Completar só existe quando a duplicata é de empresa já cadastrada: duplicata
 * de outra linha do arquivo não tem empresa a completar.
 */
function DuplicateChoiceControl({
  row,
  choice,
  onChoose,
}: {
  readonly row: ImportRow;
  readonly choice: DuplicateChoice | null;
  readonly onChoose: (rowNumber: number, choice: DuplicateChoice | null) => void;
}): React.JSX.Element {
  if (row.duplicateOfCompanyId === undefined) {
    return (
      <label className="mt-2 flex items-center gap-2 text-slate-800">
        <input
          type="checkbox"
          checked={choice === 'new'}
          onChange={(event) => {
            onChoose(row.rowNumber, event.target.checked ? 'new' : null);
          }}
        />
        Importar mesmo assim, como empresa nova
      </label>
    );
  }

  const options: readonly [DuplicateChoice | null, string][] = [
    [null, 'Não importar'],
    ['merge', 'Completar os campos vazios da empresa existente'],
    ['new', 'Importar como empresa nova'],
  ];

  return (
    <fieldset className="mt-2">
      <legend className="sr-only">O que fazer com a linha {row.rowNumber}</legend>
      <div className="flex flex-wrap gap-x-4 gap-y-1">
        {options.map(([value, label]) => (
          <label key={label} className="flex items-center gap-2 text-slate-800">
            <input
              type="radio"
              name={`linha-${row.rowNumber}`}
              checked={choice === value}
              onChange={() => {
                onChoose(row.rowNumber, value);
              }}
            />
            {label}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
