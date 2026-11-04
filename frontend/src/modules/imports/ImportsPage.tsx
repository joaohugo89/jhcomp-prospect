import { ChevronRight, Download, ShieldCheck } from 'lucide-react';
import { useState } from 'react';

import { formatDateTime } from '../../app/format';
import { Link } from '../../app/router';
import { useLastReady, useLoad } from '../../app/useLoad';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, card } from '../../components/styles';
import { getImportSummary, listImports } from '../../services/importService';
import type { ImportBatch, ImportStatus, ImportSummary } from '../../types/import';
import { ImportResultBar, ImportStatusBadge } from './ImportVisuals';
import { UploadForm } from './UploadForm';

/** Cabeçalhos que o backend reconhece sozinho (ImportField). Só cabeçalho: nenhum dado no modelo. */
const TEMPLATE_HEADER = 'nome;nome fantasia;segmento;cidade;uf;site;instagram;telefone;email;endereco;observacoes';

function downloadTemplate(): void {
  // BOM: o Excel abre como UTF-8 e mantém os acentos.
  const blob = new Blob([`﻿${TEMPLATE_HEADER}\n`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'modelo-empresas.csv';
  anchor.click();
  URL.revokeObjectURL(url);
}

const FILTERS: readonly [ImportStatus | '', string][] = [
  ['', 'Todas'],
  ['ANALYZED', 'Aguardando revisão'],
  ['COMPLETED', 'Importadas'],
  ['DISCARDED', 'Descartadas'],
];

/** Importação CSV (RF-015, D23): enviar, acompanhar o que falta revisar e consultar o histórico. */
export function ImportsPage(): React.JSX.Element {
  const [filter, setFilter] = useState<ImportStatus | ''>('');
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${filter}|${page}`, (signal) => listImports(filter, page, signal));
  const shown = useLastReady(state);
  const [summary] = useLoad('summary', (signal) => getImportSummary(signal));

  function choose(next: ImportStatus | ''): void {
    setFilter(next);
    setPage(0);
  }

  return (
    <>
      <PageHeader
        title="Importações"
        description="Envie um CSV para revisar antes de gravar. Nenhuma empresa é criada até você confirmar."
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <UploadForm />
        <aside aria-labelledby="como-importar" className={`${card} space-y-4 p-5 text-sm`}>
          <h2 id="como-importar" className="flex items-center gap-2 font-semibold text-slate-900">
            <ShieldCheck aria-hidden="true" className="size-4 text-emerald-600" />
            Como a importação protege a base
          </h2>
          <ul className="space-y-2 text-slate-600">
            <li>Nada é gravado na análise: você revisa linha a linha antes de confirmar.</li>
            <li>Possíveis duplicatas só entram se você escolher, como nova ou completando a existente.</li>
            <li>Dados já cadastrados nunca são sobrescritos; só campos vazios são completados.</li>
            <li>Errou? Quem importou pode desfazer por 7 dias as empresas que ainda não têm atividade.</li>
          </ul>
          <button type="button" className={`${buttonSecondary} w-full gap-1.5`} onClick={downloadTemplate}>
            <Download aria-hidden="true" className="size-4" />
            Baixar modelo CSV
          </button>
        </aside>
      </div>

      <section aria-labelledby="historico" className="mt-8">
        <h2 id="historico" className="mb-3 text-base font-semibold text-slate-900">
          Histórico
        </h2>
        <Overview summary={summary.kind === 'ready' ? summary.data : null} onFilter={choose} />

        <div role="group" aria-label="Filtrar por situação" className="mb-3 flex flex-wrap gap-1.5">
          {FILTERS.map(([value, label]) => (
            <button
              key={label}
              type="button"
              aria-pressed={filter === value}
              className={`h-8 rounded-full px-3 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
                filter === value
                  ? 'bg-slate-900 text-white'
                  : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'
              }`}
              onClick={() => {
                choose(value);
              }}
            >
              {label}
            </button>
          ))}
        </div>

        {shown === null || (state.kind !== 'ready' && state.kind !== 'loading') ? (
          <LoadStateView state={state} onRetry={reload} />
        ) : shown.content.length === 0 ? (
          <EmptyState
            title={filter === '' ? 'Nenhuma importação ainda.' : 'Nenhuma importação nesta situação.'}
            {...(filter === ''
              ? { description: 'Comece enviando um CSV acima. Use o modelo se a planilha ainda não existe.' }
              : {})}
          />
        ) : (
          <div className={`transition-opacity ${state.kind === 'loading' ? 'opacity-60' : ''}`}>
            <History items={shown.content} />
            <Pagination page={shown} onChange={setPage} />
          </div>
        )}
      </section>
    </>
  );
}

function Overview({
  summary,
  onFilter,
}: {
  readonly summary: ImportSummary | null;
  readonly onFilter: (status: ImportStatus) => void;
}): React.JSX.Element {
  const pending = summary?.pendingReview ?? 0;
  const tiles: readonly [string, number | undefined, string, ImportStatus | null][] = [
    ['Aguardando revisão', summary?.pendingReview, pending > 0 ? 'text-amber-700' : 'text-slate-900', 'ANALYZED'],
    ['Importadas', summary?.completed, 'text-slate-900', 'COMPLETED'],
    ['Desfeitas', summary?.undone, 'text-slate-900', null],
    ['Empresas trazidas', summary?.companiesImported, 'text-slate-900', null],
  ];
  return (
    <dl className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
      {tiles.map(([label, value, tone, status]) => {
        const content = (
          <>
            <dt className="text-xs font-medium text-slate-500">{label}</dt>
            <dd className={`mt-1 text-2xl font-bold tabular-nums ${tone}`}>{value ?? '—'}</dd>
          </>
        );
        return status === null ? (
          <div key={label} className={`${card} px-4 py-3`}>
            {content}
          </div>
        ) : (
          <div key={label} className={`${card} relative px-4 py-3 hover:bg-slate-50`}>
            {content}
            <button
              type="button"
              className="absolute inset-0 rounded-2xl focus-visible:outline-2 focus-visible:outline-brand-500"
              onClick={() => {
                onFilter(status);
              }}
            >
              <span className="sr-only">{`Ver: ${label}`}</span>
            </button>
          </div>
        );
      })}
    </dl>
  );
}

function History({ items }: { readonly items: readonly ImportBatch[] }): React.JSX.Element {
  return (
    <div className="relative overflow-x-auto rounded-2xl border border-slate-200/70 bg-white shadow-sm">
      <table className="min-w-full text-sm">
        <caption className="sr-only">Importações</caption>
        <thead className="border-b border-slate-200 bg-slate-50/70 text-left text-xs text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Arquivo
            </th>
            <th scope="col" className="px-4 py-2.5 font-medium">
              Situação
            </th>
            <th scope="col" className="hidden px-4 py-2.5 font-medium md:table-cell">
              Resultado
            </th>
            <th scope="col" className="hidden px-4 py-2.5 font-medium lg:table-cell">
              Enviada em
            </th>
            <th scope="col" className="w-10 px-4 py-2.5">
              <span className="sr-only">Abrir</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((batch) => (
            <tr key={batch.id} className={batch.status === 'ANALYZED' ? 'bg-amber-50/40' : ''}>
              <td className="max-w-[20rem] px-4 py-3">
                <Link
                  to={`/imports/${batch.id}`}
                  className="block truncate font-semibold text-slate-900 hover:text-brand-700 focus-visible:outline-2 focus-visible:outline-brand-500"
                >
                  {batch.fileName}
                </Link>
                <p className="truncate text-xs text-slate-500">{batch.sourceName}</p>
              </td>
              <td className="px-4 py-3">
                <ImportStatusBadge batch={batch} />
              </td>
              <td className="hidden px-4 py-3 md:table-cell">
                <ImportResultBar batch={batch} />
              </td>
              <td className="hidden px-4 py-3 whitespace-nowrap text-slate-600 lg:table-cell">
                {formatDateTime(batch.createdAt)}
              </td>
              <td className="px-4 py-3 text-right">
                {batch.status === 'ANALYZED' ? (
                  <Link to={`/imports/${batch.id}`} className={`${buttonPrimary} h-8 px-3 py-0 text-xs`}>
                    Revisar
                  </Link>
                ) : (
                  <Link
                    to={`/imports/${batch.id}`}
                    aria-label={`Abrir ${batch.fileName}`}
                    className="inline-flex rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus-visible:outline-2 focus-visible:outline-brand-500"
                  >
                    <ChevronRight aria-hidden="true" className="size-4" />
                  </Link>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
