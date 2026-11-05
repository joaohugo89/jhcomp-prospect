import { AlarmClock, ArrowDown, ArrowUp, ArrowUpDown, ChevronRight } from 'lucide-react';

import { formatCurrency, formatDateTime } from '../../app/format';
import { Link } from '../../app/router';
import { PriorityBadge, ScoreIndicator, StatusBadge } from '../../components/Badges';
import type { WorklistFilters, WorklistItem, WorklistSort } from '../../types/worklist';
import { daysLabel, STALE_DAYS } from '../crm/pipelineSignals';

const DAY = 86_400_000;

/** Etapas em andamento: só nelas o tempo parado pede ação. */
const IN_PROGRESS = new Set(['QUALIFIED', 'CONTACT_APPROVED', 'CONTACTED', 'IN_CONVERSATION', 'PROPOSAL_SENT']);

interface WorklistTableProps {
  readonly items: readonly WorklistItem[];
  /** Referência dos sinais (dias na etapa, atraso), fixa por carga. */
  readonly now: number;
  readonly sort: WorklistSort;
  readonly direction: WorklistFilters['direction'];
  readonly onSort: (sort: WorklistSort, direction: WorklistFilters['direction']) => void;
  readonly selected: ReadonlySet<string>;
  readonly onSelect: (ids: ReadonlySet<string>) => void;
  /** Lead com o resumo aberto ao lado. */
  readonly previewId: string | null;
  readonly onPreview: (leadId: string) => void;
}

interface SortableColumn {
  readonly sort: WorklistSort;
  readonly label: string;
  /** Primeiro clique: o que o operador quer ver primeiro (maior valor, mais tempo parado). */
  readonly firstDirection: WorklistFilters['direction'];
  /**
   * "Na etapa" ordena pela data de entrada: entrada mais antiga primeiro é
   * mais dias primeiro, então o sentido mostrado é o inverso do enviado.
   */
  readonly inverted?: boolean;
  readonly className?: string;
}

const COLUMNS: Readonly<Record<'company' | 'nextTask' | 'stage' | 'value' | 'score', SortableColumn>> = {
  company: { sort: 'COMPANY', label: 'Empresa', firstDirection: 'asc' },
  nextTask: { sort: 'NEXT_TASK', label: 'Próxima tarefa', firstDirection: 'asc', className: 'hidden lg:table-cell' },
  stage: { sort: 'STAGE', label: 'Na etapa', firstDirection: 'asc', inverted: true, className: 'hidden md:table-cell' },
  value: { sort: 'VALUE', label: 'Valor', firstDirection: 'desc', className: 'hidden md:table-cell text-right' },
  score: { sort: 'SCORE', label: 'Score', firstDirection: 'desc', className: 'hidden xl:table-cell text-right' },
};

/**
 * Fila de leads: cada linha responde "o que faço com este lead?". Clicar na
 * linha abre o resumo ao lado; o nome é um botão, para o teclado chegar lá.
 * Cabeçalhos ordenam no servidor; a seleção alimenta as ações em lote.
 */
export function WorklistTable(props: WorklistTableProps): React.JSX.Element {
  const { items, now, selected, onSelect, previewId, onPreview } = props;
  const allSelected = items.length > 0 && items.every((item) => selected.has(item.leadId));

  function toggle(leadId: string): void {
    const next = new Set(selected);
    if (next.has(leadId)) {
      next.delete(leadId);
    } else {
      next.add(leadId);
    }
    onSelect(next);
  }

  return (
    // relative: os textos só para leitor de tela (sr-only, posição absoluta) ficam presos
    // aqui; sem isso escapam da rolagem da tabela e alargam a página no celular.
    <div className="relative overflow-x-auto rounded-2xl border border-stone-200 bg-white">
      <table className="min-w-full text-sm">
        <caption className="sr-only">Leads</caption>
        <thead className="border-b border-stone-200 bg-stone-50/70 text-left text-xs text-stone-500">
          <tr>
            <th scope="col" className="w-10 px-3 py-2">
              <input
                type="checkbox"
                aria-label="Selecionar todos os leads da página"
                checked={allSelected}
                onChange={() => {
                  onSelect(allSelected ? new Set() : new Set(items.map((item) => item.leadId)));
                }}
              />
            </th>
            <SortHeader column={COLUMNS.company} {...props} />
            <th scope="col" className="px-3 py-2 font-medium">
              Status
            </th>
            <th scope="col" className="hidden px-3 py-2 font-medium lg:table-cell">
              Responsável
            </th>
            <SortHeader column={COLUMNS.nextTask} {...props} />
            <SortHeader column={COLUMNS.stage} {...props} />
            <SortHeader column={COLUMNS.value} {...props} />
            <SortHeader column={COLUMNS.score} {...props} />
            <th scope="col" className="w-10 px-3 py-2">
              <span className="sr-only">Abrir</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
          {items.map((item) => (
            <Row
              key={item.leadId}
              item={item}
              now={now}
              checked={selected.has(item.leadId)}
              previewed={previewId === item.leadId}
              onToggle={() => {
                toggle(item.leadId);
              }}
              onPreview={() => {
                onPreview(item.leadId);
              }}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SortHeader({
  column,
  sort,
  direction,
  onSort,
}: { readonly column: SortableColumn } & Pick<WorklistTableProps, 'sort' | 'direction' | 'onSort'>): React.JSX.Element {
  const active = sort === column.sort;
  // Sentido como o operador vê (mais dias primeiro = decrescente), não como o servidor recebe.
  const shown = active ? (column.inverted === true ? (direction === 'asc' ? 'desc' : 'asc') : direction) : null;
  const Icon = shown === null ? ArrowUpDown : shown === 'asc' ? ArrowUp : ArrowDown;
  const isRight = column.className?.includes('text-right') === true;

  return (
    <th
      scope="col"
      aria-sort={shown === null ? 'none' : shown === 'asc' ? 'ascending' : 'descending'}
      className={`px-3 py-2 font-medium ${column.className ?? ''}`}
    >
      <button
        type="button"
        className={`inline-flex items-center gap-1 rounded hover:text-stone-900 focus-visible:outline-2 focus-visible:outline-brand-500 ${
          active ? 'text-stone-900' : ''
        } ${isRight ? 'flex-row-reverse' : ''}`}
        onClick={() => {
          const next = active ? (direction === 'asc' ? 'desc' : 'asc') : column.firstDirection;
          onSort(column.sort, next);
        }}
      >
        {column.label}
        <Icon aria-hidden="true" className={`size-3.5 ${active ? '' : 'text-stone-300'}`} />
      </button>
    </th>
  );
}

function Row({
  item,
  now,
  checked,
  previewed,
  onToggle,
  onPreview,
}: {
  readonly item: WorklistItem;
  readonly now: number;
  readonly checked: boolean;
  readonly previewed: boolean;
  readonly onToggle: () => void;
  readonly onPreview: () => void;
}): React.JSX.Element {
  const days =
    item.stageEnteredAt === undefined
      ? undefined
      : Math.max(0, Math.floor((now - Date.parse(item.stageEnteredAt)) / DAY));
  const stale = days !== undefined && days >= STALE_DAYS && IN_PROGRESS.has(item.status);
  const overdue = item.nextTaskDueAt !== undefined && Date.parse(item.nextTaskDueAt) < now;
  const place = [item.segment, `${item.city}/${item.state}`].filter((part) => part !== undefined).join(' · ');

  return (
    <tr
      className={`cursor-pointer ${previewed ? 'bg-brand-50/60' : checked ? 'bg-stone-50' : 'hover:bg-stone-50'}`}
      onClick={onPreview}
    >
      <td className="px-3 py-2">
        <input
          type="checkbox"
          aria-label={`Selecionar ${item.companyName}`}
          checked={checked}
          onClick={(event) => {
            event.stopPropagation();
          }}
          onChange={onToggle}
        />
      </td>
      <td className="max-w-[18rem] px-3 py-2">
        <button
          type="button"
          aria-expanded={previewed}
          aria-controls="previa-lead"
          className="block max-w-full truncate text-left font-semibold text-stone-900 hover:text-brand-700 focus-visible:outline-2 focus-visible:outline-brand-500"
          onClick={(event) => {
            event.stopPropagation();
            onPreview();
          }}
        >
          {item.companyName}
        </button>
        <p className="truncate text-xs text-stone-500">{place}</p>
      </td>
      <td className="px-3 py-2">
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusBadge status={item.status} />
          {item.priority === 'HIGH' || item.priority === 'VERY_HIGH' ? (
            <PriorityBadge priority={item.priority} />
          ) : null}
        </div>
      </td>
      <td className="hidden px-3 py-2 text-stone-700 lg:table-cell">
        {item.assigneeName ?? <span className="text-stone-400">Sem responsável</span>}
      </td>
      <td className="hidden max-w-[14rem] px-3 py-2 lg:table-cell">
        {item.nextTaskTitle === undefined ? (
          <span className="text-stone-400">Nenhuma</span>
        ) : (
          <>
            <p className="truncate text-stone-700">{item.nextTaskTitle}</p>
            {item.nextTaskDueAt !== undefined && (
              <p
                className={`flex items-center gap-1 text-xs ${overdue ? 'font-semibold text-red-700' : 'text-stone-500'}`}
              >
                {overdue && <AlarmClock aria-hidden="true" className="size-3" />}
                {`${overdue ? 'Atrasada · ' : ''}${formatDateTime(item.nextTaskDueAt)}`}
              </p>
            )}
          </>
        )}
      </td>
      <td className="hidden px-3 py-2 whitespace-nowrap md:table-cell">
        {days === undefined ? (
          '—'
        ) : (
          <span
            className={stale ? 'rounded-md bg-amber-50 px-1.5 py-0.5 font-medium text-amber-800' : 'text-stone-600'}
          >
            {days === 0 ? 'hoje' : daysLabel(days)}
            {stale && <span className="sr-only">{' (parado)'}</span>}
          </span>
        )}
      </td>
      <td className="hidden px-3 py-2 text-right whitespace-nowrap text-stone-900 tabular-nums md:table-cell">
        {item.estimatedValue === undefined ? (
          <span className="text-stone-400">—</span>
        ) : (
          formatCurrency(item.estimatedValue)
        )}
      </td>
      <td className="hidden px-3 py-2 text-right xl:table-cell">
        {item.score === undefined ? <span className="text-stone-400">—</span> : <ScoreIndicator score={item.score} />}
      </td>
      <td className="px-3 py-2">
        <Link
          to={`/leads/${item.leadId}`}
          aria-label={`Abrir ${item.companyName}`}
          className="inline-flex rounded-md p-1 text-stone-400 hover:bg-stone-100 hover:text-stone-700 focus-visible:outline-2 focus-visible:outline-brand-500"
        >
          <ChevronRight aria-hidden="true" className="size-4" />
        </Link>
      </td>
    </tr>
  );
}
