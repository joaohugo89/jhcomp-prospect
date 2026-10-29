import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { IMPORT_FIELD_LABEL, IMPORT_STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { LoadStateView } from '../../components/States';
import { link } from '../../components/styles';
import { getImport } from '../../services/importService';
import { IMPORT_FIELDS, type DuplicateChoice, type ImportBatch } from '../../types/import';
import { ImportReport } from './ImportReport';
import { ImportReview } from './ImportReview';

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
