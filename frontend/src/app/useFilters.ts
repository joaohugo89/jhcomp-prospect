import { useState } from 'react';

/**
 * Filtros de listagem: rascunho do formulário, filtros aplicados e página.
 *
 * O rascunho e os filtros aplicados ficam separados para que a busca só
 * dispare ao enviar o formulário, e não a cada tecla. Aplicar volta à
 * primeira página, porque a página atual pode nem existir no novo resultado.
 *
 * @param empty filtros sem efeito; precisa ser uma constante de módulo
 * @param initial filtros já aplicados ao abrir (por exemplo, lidos da URL)
 */
export function useFilters<F>(empty: F, initial: F = empty) {
  const [draft, setDraft] = useState<F>(initial);
  const [applied, setApplied] = useState<F>(initial);
  const [page, setPage] = useState(0);

  return {
    draft,
    setDraft,
    applied,
    page,
    setPage,
    apply: (): void => {
      setPage(0);
      setApplied(draft);
    },
    /** Distingue "nada cadastrado" de "nada corresponde aos filtros". */
    isFiltered: JSON.stringify(applied) !== JSON.stringify(empty),
    /** Chave da consulta para o `useLoad`. */
    queryKey: JSON.stringify({ applied, page }),
  };
}
