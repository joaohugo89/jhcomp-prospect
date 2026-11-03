import { useId, useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { buttonSecondary, input } from '../../components/styles';
import { bulkUpdateCompanies, type CompanyBulkChange } from '../../services/companyService';
import type { CompanyWorklistFilters } from '../../types/company';

/** Recortes de um clique. "Sem segmento" é o mesmo filtro do seletor de segmento. */
type Quick = 'all' | 'withoutLead' | 'withoutContact' | 'withoutSegment' | 'duplicate' | 'archived';

const QUICK: readonly [Quick, string][] = [
  ['all', 'Todas'],
  ['withoutLead', 'Sem lead'],
  ['withoutContact', 'Sem contato'],
  ['withoutSegment', 'Sem segmento'],
  ['duplicate', 'Possível duplicata'],
  ['archived', 'Arquivadas'],
];

function quickOf(filters: CompanyWorklistFilters): Quick | null {
  if (filters.focus === 'WITHOUT_LEAD') return 'withoutLead';
  if (filters.focus === 'WITHOUT_CONTACT') return 'withoutContact';
  if (filters.focus === 'DUPLICATE') return 'duplicate';
  if (filters.focus === 'ARCHIVED') return 'archived';
  if (filters.noSegment) return 'withoutSegment';
  return 'all';
}

function applyQuick(filters: CompanyWorklistFilters, quick: Quick): CompanyWorklistFilters {
  const base: CompanyWorklistFilters = { ...filters, focus: '', noSegment: false };
  switch (quick) {
    case 'withoutLead':
      return { ...base, focus: 'WITHOUT_LEAD' };
    case 'withoutContact':
      return { ...base, focus: 'WITHOUT_CONTACT' };
    case 'withoutSegment':
      return { ...base, noSegment: true, segment: '' };
    case 'duplicate':
      return { ...base, focus: 'DUPLICATE' };
    case 'archived':
      return { ...base, focus: 'ARCHIVED' };
    default:
      return base;
  }
}

export function CompanyQuickFilters({
  filters,
  onApply,
}: {
  readonly filters: CompanyWorklistFilters;
  readonly onApply: (filters: CompanyWorklistFilters) => void;
}): React.JSX.Element {
  const active = quickOf(filters);
  return (
    <div role="group" aria-label="Recortes rápidos" className="flex flex-wrap gap-1.5">
      {QUICK.map(([key, label]) => (
        <button
          key={key}
          type="button"
          aria-pressed={active === key}
          className={`h-8 rounded-full px-3 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
            active === key
              ? 'bg-slate-900 text-white'
              : 'bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50'
          }`}
          onClick={() => {
            onApply(applyQuick(filters, key));
          }}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

/**
 * Qualidade de dado em lote: segmento (na grafia que já existe, D31) e cidade
 * com UF. Arquivadas não mudam e são contadas à parte.
 */
export function CompanyBulkBar({
  selected,
  segments,
  onDone,
  onClear,
}: {
  readonly selected: ReadonlySet<string>;
  readonly segments: readonly string[];
  readonly onDone: (message: string) => void;
  readonly onClear: () => void;
}): React.JSX.Element {
  const [segment, setSegment] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listId = useId();

  async function run(change: CompanyBulkChange): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      const { updated, skippedArchived } = await bulkUpdateCompanies([...selected], change);
      const skipped =
        skippedArchived === 0
          ? ''
          : skippedArchived === 1
            ? ' 1 arquivada não mudou.'
            : ` ${skippedArchived} arquivadas não mudaram.`;
      onDone(`${updated} ${updated === 1 ? 'empresa atualizada' : 'empresas atualizadas'}.${skipped}`);
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  const validState = /^[A-Za-z]{2}$/.test(state.trim());

  return (
    <div
      role="region"
      aria-label="Ações em lote"
      className="flex flex-wrap items-center gap-3 rounded-2xl bg-slate-900 px-4 py-3 text-sm text-white shadow-lg"
    >
      <span className="font-semibold">{`${selected.size} ${selected.size === 1 ? 'selecionada' : 'selecionadas'}`}</span>
      <div className="flex items-center gap-2">
        <label>
          <span className="sr-only">Segmento para as selecionadas</span>
          <input
            list={listId}
            placeholder="Segmento…"
            maxLength={80}
            className={`${input} mt-0 h-9 w-44 py-0 text-slate-900`}
            value={segment}
            disabled={busy}
            onChange={(event) => {
              setSegment(event.target.value);
            }}
          />
        </label>
        <datalist id={listId}>
          {segments.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </datalist>
        <button
          type="button"
          className={`${buttonSecondary} h-9 py-0`}
          disabled={busy || segment.trim() === ''}
          onClick={() => {
            void run({ segment: segment.trim() });
          }}
        >
          Definir segmento
        </button>
      </div>
      <div className="flex items-center gap-2">
        <label>
          <span className="sr-only">Cidade para as selecionadas</span>
          <input
            placeholder="Cidade…"
            maxLength={120}
            className={`${input} mt-0 h-9 w-40 py-0 text-slate-900`}
            value={city}
            disabled={busy}
            onChange={(event) => {
              setCity(event.target.value);
            }}
          />
        </label>
        <label>
          <span className="sr-only">UF para as selecionadas</span>
          <input
            placeholder="UF"
            maxLength={2}
            className={`${input} mt-0 h-9 w-16 py-0 text-slate-900 uppercase`}
            value={state}
            disabled={busy}
            onChange={(event) => {
              setState(event.target.value);
            }}
          />
        </label>
        <button
          type="button"
          className={`${buttonSecondary} h-9 py-0`}
          disabled={busy || city.trim() === '' || !validState}
          onClick={() => {
            void run({ city: city.trim(), state: state.trim().toUpperCase() });
          }}
        >
          Corrigir cidade
        </button>
      </div>
      <button type="button" className="ml-auto text-xs font-semibold text-slate-300 hover:text-white" onClick={onClear}>
        Limpar seleção
      </button>
      {error !== null && (
        <p role="alert" className="basis-full text-sm text-red-200">
          {error}
        </p>
      )}
    </div>
  );
}
