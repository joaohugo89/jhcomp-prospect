import { Check, CheckCircle2, Clock, Loader2, Undo2, XCircle } from 'lucide-react';

import type { ImportBatch } from '../../types/import';

/**
 * The import's visual pieces: status with icon and text (never colour
 * alone), the per-row result bar, and the three steps of the flow (D23).
 */

const STATUS_STYLE = {
  ANALYZED: { label: 'Aguardando revisão', icon: Clock, tone: 'bg-amber-50 text-amber-800 ring-amber-200' },
  IMPORTING: { label: 'Importando', icon: Loader2, tone: 'bg-sky-50 text-sky-800 ring-sky-200' },
  COMPLETED: { label: 'Importada', icon: CheckCircle2, tone: 'bg-emerald-50 text-emerald-800 ring-emerald-200' },
  DISCARDED: { label: 'Descartada', icon: XCircle, tone: 'bg-stone-100 text-stone-600 ring-stone-200' },
  UNDONE: { label: 'Desfeita', icon: Undo2, tone: 'bg-stone-100 text-stone-700 ring-stone-300' },
} as const;

export function ImportStatusBadge({ batch }: { readonly batch: ImportBatch }): React.JSX.Element {
  const style = STATUS_STYLE[batch.undoneAt === undefined ? batch.status : 'UNDONE'];
  const Icon = style.icon;
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap ring-1 ${style.tone}`}
    >
      <Icon aria-hidden="true" className="size-3.5" />
      {style.label}
    </span>
  );
}

interface Segment {
  /** Singular and plural, which Portuguese inflects: "1 importada", "2 importadas". */
  readonly label: readonly [string, string];
  readonly value: number;
  readonly tone: string;
}

/** Result segments: during review, what the file brought; afterwards, what became a company. */
function segmentsOf(batch: ImportBatch): readonly Segment[] {
  if (batch.status === 'ANALYZED' || batch.status === 'IMPORTING') {
    return [
      { label: ['válida', 'válidas'], value: batch.validRows, tone: 'bg-emerald-500' },
      { label: ['possível duplicata', 'possíveis duplicatas'], value: batch.duplicateRows, tone: 'bg-amber-400' },
      { label: ['rejeitada', 'rejeitadas'], value: batch.rejectedRows, tone: 'bg-red-400' },
    ];
  }
  const used = batch.importedRows + batch.mergedRows;
  return [
    { label: ['importada', 'importadas'], value: batch.importedRows, tone: 'bg-emerald-500' },
    { label: ['completou empresa', 'completaram empresa'], value: batch.mergedRows, tone: 'bg-sky-500' },
    { label: ['rejeitada', 'rejeitadas'], value: batch.rejectedRows, tone: 'bg-red-400' },
    {
      label: ['não importada', 'não importadas'],
      value: Math.max(0, batch.totalRows - used - batch.rejectedRows),
      tone: 'bg-stone-300',
    },
  ];
}

/** A proportion bar with the text beside it: colour only reinforces what is written. */
export function ImportResultBar({ batch }: { readonly batch: ImportBatch }): React.JSX.Element {
  if (batch.status === 'DISCARDED') {
    return <span className="text-xs text-stone-500">{`${batch.totalRows} lidas, nada gravado`}</span>;
  }
  const segments = segmentsOf(batch).filter((segment) => segment.value > 0);
  const total = Math.max(1, batch.totalRows);
  return (
    <div className="min-w-[10rem]">
      <div aria-hidden="true" className="flex h-1.5 overflow-hidden rounded-full bg-stone-100">
        {segments.map((segment) => (
          <span
            key={segment.label[1]}
            className={`${segment.tone} border-r-2 border-chart-gap last:border-r-0`}
            style={{ width: `${(segment.value / total) * 100}%` }}
          />
        ))}
      </div>
      <p className="mt-1 text-xs text-stone-600">
        {segments.length === 0
          ? 'Nenhuma linha'
          : segments.map((s) => `${s.value} ${s.value === 1 ? s.label[0] : s.label[1]}`).join(' · ')}
      </p>
    </div>
  );
}

const STEPS = ['Enviar arquivo', 'Revisar linhas', 'Importar'] as const;

/**
 * The three steps of an import. `current` is the step in progress (0 to 2);
 * `done` marks the last one as finished.
 */
export function ImportSteps({
  current,
  done = false,
}: {
  readonly current: number;
  readonly done?: boolean;
}): React.JSX.Element {
  return (
    <ol aria-label="Etapas da importação" className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
      {STEPS.map((label, index) => {
        const complete = index < current || (done && index === current);
        const active = index === current && !done;
        return (
          <li key={label} className="flex items-center gap-2">
            <span
              {...(active ? { 'aria-current': 'step' as const } : {})}
              className={`inline-flex items-center gap-1.5 font-semibold ${
                active ? 'text-brand-700' : complete ? 'text-stone-700' : 'text-stone-400'
              }`}
            >
              <span
                aria-hidden="true"
                className={`inline-flex size-5 items-center justify-center rounded-full text-[11px] ${
                  complete
                    ? 'bg-emerald-600 text-white'
                    : active
                      ? 'bg-brand-600 text-white'
                      : 'bg-stone-100 text-stone-500 ring-1 ring-stone-200'
                }`}
              >
                {complete ? <Check className="size-3" /> : index + 1}
              </span>
              {label}
              {complete && <span className="sr-only">{' (concluída)'}</span>}
            </span>
            {index < STEPS.length - 1 && <span aria-hidden="true" className="h-px w-6 bg-stone-200" />}
          </li>
        );
      })}
    </ol>
  );
}
