export interface Column<T> {
  readonly header: string;
  readonly render: (row: T) => React.ReactNode;
  /** Oculta a coluna em telas estreitas; a primeira coluna nunca deve usar. */
  readonly hideOnSmall?: boolean;
}

interface DataTableProps<T> {
  readonly caption: string;
  readonly columns: readonly Column<T>[];
  readonly rows: readonly T[];
  readonly rowKey: (row: T) => string;
}

/**
 * Tabela de listagem (doc 08 §6, §13).
 *
 * Em telas estreitas, rola na horizontal dentro do próprio contêiner, e
 * colunas secundárias podem ser ocultadas: a página nunca rola de lado.
 */
export function DataTable<T>({ caption, columns, rows, rowKey }: DataTableProps<T>): React.JSX.Element {
  return (
    // relative: a legenda sr-only (posição absoluta) fica presa aqui e não alarga a página.
    <div className="relative overflow-x-auto rounded-lg border border-slate-200 bg-white">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-slate-50">
          <tr>
            {columns.map((column) => (
              <th
                key={column.header}
                scope="col"
                className={`px-4 py-2 text-left font-medium text-slate-600 ${column.hideOnSmall === true ? 'hidden md:table-cell' : ''}`}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row) => (
            <tr key={rowKey(row)} className="hover:bg-slate-50">
              {columns.map((column) => (
                <td
                  key={column.header}
                  className={`px-4 py-2 align-top text-slate-800 ${column.hideOnSmall === true ? 'hidden md:table-cell' : ''}`}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
