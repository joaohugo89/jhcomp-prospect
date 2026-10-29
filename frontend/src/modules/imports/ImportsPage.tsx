import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { IMPORT_STATUS_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { DataTable, type Column } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { link } from '../../components/styles';
import { listImports } from '../../services/importService';
import { type ImportBatch } from '../../types/import';
import { UploadForm } from './UploadForm';

const COLUMNS: readonly Column<ImportBatch>[] = [
  {
    header: 'Arquivo',
    render: (batch) => (
      <Link to={`/imports/${batch.id}`} className={`${link} break-all`}>
        {batch.fileName}
      </Link>
    ),
  },
  { header: 'Situação', render: (batch) => IMPORT_STATUS_LABEL[batch.status] },
  {
    header: 'Linhas',
    render: (batch) =>
      batch.status === 'COMPLETED' ? `${batch.importedRows} de ${batch.totalRows} importadas` : `${batch.totalRows} lidas`,
  },
  { header: 'Origem', render: (batch) => batch.sourceName, hideOnSmall: true },
  { header: 'Enviada em', render: (batch) => formatDateTime(batch.createdAt), hideOnSmall: true },
];

/** Importação CSV (RF-015): envio e histórico. */
export function ImportsPage(): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(String(page), (signal) => listImports(page, signal));

  return (
    <>
      <PageHeader
        title="Importações"
        description="Envie um CSV para revisar antes de gravar. Nenhuma empresa é criada até você confirmar."
      />
      <UploadForm />

      <h2 className="mb-3 mt-8 text-base font-semibold">Histórico</h2>
      {state.kind === 'ready' ? (
        state.data.content.length === 0 ? (
          <EmptyState title="Nenhuma importação ainda." />
        ) : (
          <>
            <DataTable caption="Importações" columns={COLUMNS} rows={state.data.content} rowKey={(row) => row.id} />
            <Pagination page={state.data} onChange={setPage} />
          </>
        )
      ) : (
        <LoadStateView state={state} onRetry={reload} />
      )}
    </>
  );
}
