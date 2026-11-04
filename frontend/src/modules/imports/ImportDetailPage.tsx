import { ArrowLeft } from 'lucide-react';
import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { IMPORT_FIELD_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { LoadStateView } from '../../components/States';
import { card, link } from '../../components/styles';
import { getImport } from '../../services/importService';
import { IMPORT_FIELDS, type DuplicateChoice, type ImportBatch } from '../../types/import';
import { ImportReport } from './ImportReport';
import { ImportReview } from './ImportReview';
import { ImportStatusBadge, ImportSteps } from './ImportVisuals';
import { UndoImport } from './UndoImport';

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
      <Link
        to="/imports"
        className="mb-4 inline-flex items-center gap-1 text-sm font-medium text-slate-600 hover:text-slate-900"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Voltar para importações
      </Link>
      <header className="mb-5 space-y-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-bold tracking-tight break-all text-slate-900">{batch.fileName}</h1>
          <ImportStatusBadge batch={batch} />
        </div>
        <p className="text-sm text-slate-500">
          {`Origem: ${batch.sourceName} · enviada em ${formatDateTime(batch.createdAt)}${
            batch.completedAt === undefined ? '' : ` · concluída em ${formatDateTime(batch.completedAt)}`
          }`}
        </p>
        {batch.status !== 'DISCARDED' && (
          <ImportSteps current={reviewing ? 1 : 2} done={batch.status === 'COMPLETED'} />
        )}
      </header>

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
      <UndoImport batch={batch} onUndone={reload} />
      <MappingSummary batch={batch} />

      {reviewing && (
        <ImportReview
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
        <ImportReport
          importId={batch.id}
          version={`${batch.status}:${batch.importedRows}:${batch.mergedRows}:${batch.undoneAt ?? ''}`}
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
    <dl className="mb-6 grid grid-cols-3 gap-2 sm:grid-cols-4 sm:gap-3 lg:grid-cols-7">
      {items.map(([label, value]) => (
        <div key={label} className={`${card} min-w-0 px-3 py-2.5 sm:px-4 sm:py-3`}>
          <dt className="truncate text-[11px] font-medium text-slate-500 sm:text-xs">{label}</dt>
          <dd className="mt-0.5 text-lg font-bold tabular-nums text-slate-900 sm:mt-1 sm:text-2xl">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function MappingSummary({ batch }: { readonly batch: ImportBatch }): React.JSX.Element {
  const mapped = IMPORT_FIELDS.filter((field) => batch.columnMapping[field] !== undefined);
  const ignored = batch.ignoredColumns ?? [];

  return (
    <section aria-labelledby="colunas" className={`${card} mb-6 p-5 text-sm`}>
      <h2 id="colunas" className="text-sm font-semibold text-slate-900">
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
