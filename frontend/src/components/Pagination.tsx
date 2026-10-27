import type { Page } from '../types/page';
import { buttonSecondary } from './styles';

interface PaginationProps {
  readonly page: Page<unknown>;
  readonly onChange: (page: number) => void;
}

/** Navegação entre páginas (RNF-002). A contagem é anunciada como texto. */
export function Pagination({ page, onChange }: PaginationProps): React.JSX.Element | null {
  if (page.totalPages <= 1) {
    return (
      <p className="mt-3 text-xs text-slate-500">
        {page.totalElements} {page.totalElements === 1 ? 'registro' : 'registros'}
      </p>
    );
  }

  return (
    <nav aria-label="Paginação" className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm">
      <p className="text-slate-600">
        Página {page.page + 1} de {page.totalPages} · {page.totalElements} registros
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          className={buttonSecondary}
          disabled={page.page === 0}
          onClick={() => {
            onChange(page.page - 1);
          }}
        >
          Anterior
        </button>
        <button
          type="button"
          className={buttonSecondary}
          disabled={!page.hasNext}
          onClick={() => {
            onChange(page.page + 1);
          }}
        >
          Próxima
        </button>
      </div>
    </nav>
  );
}
