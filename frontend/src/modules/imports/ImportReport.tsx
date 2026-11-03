import { useState } from 'react';

import { fieldMessage } from '../../app/errors';
import { DUPLICATE_SIGNAL_LABEL, IMPORT_FIELD_LABEL, IMPORT_ROW_STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { link } from '../../components/styles';
import { getImportReport } from '../../services/importService';
import {
  IMPORT_FIELDS,
  type DuplicateChoice,
  type ImportField,
  type ImportRow,
  type ImportRowStatus,
} from '../../types/import';

const FILTERS: readonly [ImportRowStatus | '', string][] = [
  ['', 'Todas'],
  ['VALID', 'Válidas'],
  ['DUPLICATE', 'Possíveis duplicatas'],
  ['REJECTED', 'Rejeitadas'],
  ['IMPORTED', 'Importadas'],
  ['MERGED', 'Completaram empresa'],
  ['SKIPPED', 'Não importadas'],
  ['REMOVED', 'Excluídas depois'],
];

export function ImportReport({
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
    reviewing
      ? status !== 'IMPORTED' && status !== 'MERGED' && status !== 'SKIPPED' && status !== 'REMOVED'
      : status !== 'VALID',
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
  REMOVED: 'border-slate-200',
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
