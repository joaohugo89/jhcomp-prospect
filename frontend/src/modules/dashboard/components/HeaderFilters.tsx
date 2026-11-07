import { ChevronDown, SlidersHorizontal } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import { SOURCE_TYPE_LABEL } from '../../../app/labels';
import { buttonPrimary, card, input } from '../../../components/styles';
import type { DashboardFilters } from '../../../services/crmService';
import { SOURCE_TYPES, type SourceType } from '../../../types/company';
import type { Teammate } from '../../../types/session';

export const PRESETS: readonly number[] = [7, 30, 90];

interface HeaderFiltersProps {
  readonly draft: DashboardFilters;
  readonly onDraftChange: (next: DashboardFilters) => void;
  /** Aplica o rascunho; o botão fica desabilitado com período inválido. */
  readonly onApply: () => void;
  /** Atalho: aplica na hora os últimos `days` dias, mantendo os outros filtros. */
  readonly onPreset: (days: number) => void;
  /** Atalho cujo período coincide com o aplicado; ausente é período personalizado. */
  readonly activePreset: number | undefined;
  /** Membros ativos, para filtrar por responsável. */
  readonly team: readonly Teammate[];
  /** Quem está usando: vira a opção "Meus leads". */
  readonly currentUserId: string;
}

const toolbarButton =
  'inline-flex h-9 items-center gap-1.5 rounded-xl px-3 text-xs font-semibold shadow-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500';

/**
 * Período e filtros, à direita do título. Atalhos de período aplicam na hora;
 * o período personalizado, o segmento, a cidade e a origem ficam num painel
 * e valem ao aplicar.
 */
export function HeaderFilters({
  draft,
  onDraftChange,
  onApply,
  onPreset,
  activePreset,
  team,
  currentUserId,
}: HeaderFiltersProps): React.JSX.Element {
  const [open, setOpen] = useState(false);
  const toggle = useRef<HTMLButtonElement>(null);

  // Esc fecha o painel e devolve o foco ao botão que o abriu.
  useEffect(() => {
    if (!open) {
      return undefined;
    }
    function onKey(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        setOpen(false);
        toggle.current?.focus();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  const reversed = draft.from !== '' && draft.to !== '' && draft.from > draft.to;
  const invalid = draft.from === '' || draft.to === '' || reversed;
  const extra = [draft.segment.trim(), draft.city.trim(), draft.source, draft.assignee].filter(
    (value) => value !== '',
  ).length;
  const others = team.filter((member) => member.userId !== currentUserId);

  return (
    <div className="relative flex items-center gap-2">
      <button
        ref={toggle}
        type="button"
        aria-expanded={open}
        aria-controls="painel-filtros"
        className={`${toolbarButton} border border-stone-200 glass text-stone-700 hover:bg-stone-50`}
        onClick={() => {
          setOpen(!open);
        }}
      >
        <SlidersHorizontal aria-hidden="true" className="size-4 text-stone-500" />
        Filtros
        {extra > 0 && (
          <span className="rounded-full bg-brand-600 px-1.5 text-[11px] font-semibold text-white">
            {extra}
            <span className="sr-only">{' ativos'}</span>
          </span>
        )}
      </button>

      <div className="relative">
        <label htmlFor="period" className="sr-only">
          Período
        </label>
        <select
          id="period"
          className={`${toolbarButton} appearance-none border border-brand-700 bg-brand-600 pr-7 text-white hover:bg-brand-700`}
          value={activePreset === undefined ? 'custom' : String(activePreset)}
          onChange={(event) => {
            const days = Number(event.target.value);
            if (Number.isInteger(days) && days > 0) {
              onPreset(days);
            } else {
              setOpen(true);
            }
          }}
        >
          {PRESETS.map((days) => (
            <option key={days} value={days} className="glass text-stone-900">
              {`${days}D`}
            </option>
          ))}
          <option value="custom" className="glass text-stone-900">
            Personalizado
          </option>
        </select>
        <ChevronDown
          aria-hidden="true"
          className="pointer-events-none absolute top-1/2 right-2 size-3.5 -translate-y-1/2 text-white"
        />
      </div>

      {open && (
        // Fundo do painel: tocar fora fecha, e o toque não atravessa para o que está
        // embaixo (no celular, acionaria um card ou link sem querer). Teclado fecha com Esc.
        // oxlint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions
        <div
          aria-hidden="true"
          data-testid="fundo-filtros"
          className="fixed inset-0 z-20 bg-oxblood-deep/50 sm:bg-transparent"
          onClick={() => {
            setOpen(false);
          }}
        />
      )}
      {open && (
        <form
          id="painel-filtros"
          aria-label="Filtros"
          className={`${card} glass-panel absolute top-11 right-0 z-30 grid w-[min(34rem,calc(100vw-2rem))] gap-3 p-4 shadow-lg sm:grid-cols-2`}
          onSubmit={(event) => {
            event.preventDefault();
            if (!invalid) {
              onApply();
              setOpen(false);
            }
          }}
        >
          <Field label="De">
            <input
              type="date"
              required
              className={input}
              value={draft.from}
              onChange={(event) => {
                onDraftChange({ ...draft, from: event.target.value });
              }}
            />
          </Field>
          <Field label="Até">
            <input
              type="date"
              required
              className={input}
              value={draft.to}
              onChange={(event) => {
                onDraftChange({ ...draft, to: event.target.value });
              }}
            />
          </Field>
          <Field label="Segmento">
            <input
              className={input}
              value={draft.segment}
              onChange={(event) => {
                onDraftChange({ ...draft, segment: event.target.value });
              }}
            />
          </Field>
          <Field label="Cidade">
            <input
              className={input}
              value={draft.city}
              onChange={(event) => {
                onDraftChange({ ...draft, city: event.target.value });
              }}
            />
          </Field>
          <Field label="Origem">
            <select
              className={input}
              value={draft.source}
              onChange={(event) => {
                onDraftChange({ ...draft, source: toSource(event.target.value) });
              }}
            >
              <option value="">Todas</option>
              {SOURCE_TYPES.map((type) => (
                <option key={type} value={type}>
                  {SOURCE_TYPE_LABEL[type]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Responsável">
            <select
              className={input}
              value={draft.assignee}
              onChange={(event) => {
                onDraftChange({ ...draft, assignee: event.target.value });
              }}
            >
              <option value="">Todos</option>
              <option value={currentUserId}>Meus leads</option>
              {others.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.displayName}
                </option>
              ))}
            </select>
          </Field>
          <div className="flex items-end gap-2 sm:col-span-2">
            <button type="submit" className={`${buttonPrimary} w-full`} disabled={invalid}>
              Aplicar
            </button>
          </div>
          {reversed && (
            <p role="alert" className="text-sm text-red-700 sm:col-span-2">
              A data inicial deve ser anterior à final.
            </p>
          )}
        </form>
      )}
    </div>
  );
}

function Field({ label, children }: { readonly label: string; readonly children: React.ReactNode }): React.JSX.Element {
  return (
    <label className="text-xs font-medium text-stone-600">
      {label}
      {children}
    </label>
  );
}

function toSource(value: string): SourceType | '' {
  return SOURCE_TYPES.find((type) => type === value) ?? '';
}
