import { ArrowDown, ArrowUp, ArrowUpDown, AtSign, ChevronRight, Copy, Globe, Mail, Phone } from 'lucide-react';

import { formatDateTime } from '../../app/format';
import { SOURCE_TYPE_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { StatusBadge } from '../../components/Badges';
import type { CompanySort, CompanyWorklistFilters, CompanyWorklistItem } from '../../types/company';

type Direction = CompanyWorklistFilters['direction'];

interface CompanyTableProps {
  readonly items: readonly CompanyWorklistItem[];
  readonly sort: CompanySort;
  readonly direction: Direction;
  readonly onSort: (sort: CompanySort, direction: Direction) => void;
  readonly selected: ReadonlySet<string>;
  readonly onSelect: (ids: ReadonlySet<string>) => void;
  readonly previewId: string | null;
  readonly onPreview: (companyId: string) => void;
}

interface SortableColumn {
  readonly sort: CompanySort;
  readonly label: string;
  /** First click: name from A to Z, most recent date first. */
  readonly firstDirection: Direction;
  readonly className?: string;
}

const COLUMNS: Readonly<Record<'name' | 'city' | 'activity' | 'created', SortableColumn>> = {
  name: { sort: 'NAME', label: 'Empresa', firstDirection: 'asc' },
  city: { sort: 'CITY', label: 'Cidade', firstDirection: 'asc', className: 'hidden md:table-cell' },
  activity: {
    sort: 'LAST_ACTIVITY',
    label: 'Última atividade',
    firstDirection: 'desc',
    className: 'hidden lg:table-cell',
  },
  created: { sort: 'CREATED', label: 'Cadastrada', firstDirection: 'desc', className: 'hidden xl:table-cell' },
};

/**
 * The company list as a tool (D32): each row says whether the company has a
 * lead, how to reach it, where it came from and whether it looks like a
 * duplicate. Clicking opens the preview beside it; the selection feeds the
 * bulk edit.
 */
export function CompanyTable(props: CompanyTableProps): React.JSX.Element {
  const { items, selected, onSelect, previewId, onPreview } = props;
  const allSelected = items.length > 0 && items.every((item) => selected.has(item.companyId));

  function toggle(companyId: string): void {
    const next = new Set(selected);
    if (next.has(companyId)) {
      next.delete(companyId);
    } else {
      next.add(companyId);
    }
    onSelect(next);
  }

  return (
    // relative: keeps the sr-only text (absolutely positioned) inside the table's scroll area.
    <div className="relative overflow-x-auto rounded-2xl border border-stone-200 glass">
      <table className="min-w-full text-sm">
        <caption className="sr-only">Empresas</caption>
        <thead className="border-b border-stone-200 bg-stone-50/70 text-left text-xs text-stone-500">
          <tr>
            <th scope="col" className="w-10 px-3 py-2">
              <input
                type="checkbox"
                aria-label="Selecionar todas as empresas da página"
                checked={allSelected}
                onChange={() => {
                  onSelect(allSelected ? new Set() : new Set(items.map((item) => item.companyId)));
                }}
              />
            </th>
            <SortHeader column={COLUMNS.name} {...props} />
            <th scope="col" className="px-3 py-2 font-medium">
              Lead atual
            </th>
            <th scope="col" className="hidden px-3 py-2 font-medium sm:table-cell">
              Contato
            </th>
            <SortHeader column={COLUMNS.city} {...props} />
            <th scope="col" className="hidden px-3 py-2 font-medium lg:table-cell">
              Origem
            </th>
            <SortHeader column={COLUMNS.activity} {...props} />
            <SortHeader column={COLUMNS.created} {...props} />
            <th scope="col" className="w-10 px-3 py-2">
              <span className="sr-only">Abrir</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
          {items.map((item) => (
            <Row
              key={item.companyId}
              item={item}
              checked={selected.has(item.companyId)}
              previewed={previewId === item.companyId}
              onToggle={() => {
                toggle(item.companyId);
              }}
              onPreview={() => {
                onPreview(item.companyId);
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
}: { readonly column: SortableColumn } & Pick<CompanyTableProps, 'sort' | 'direction' | 'onSort'>): React.JSX.Element {
  const active = sort === column.sort;
  const Icon = !active ? ArrowUpDown : direction === 'asc' ? ArrowUp : ArrowDown;
  return (
    <th
      scope="col"
      aria-sort={!active ? 'none' : direction === 'asc' ? 'ascending' : 'descending'}
      className={`px-3 py-2 font-medium ${column.className ?? ''}`}
    >
      <button
        type="button"
        className={`inline-flex items-center gap-1 rounded hover:text-stone-900 focus-visible:outline-2 focus-visible:outline-brand-500 ${
          active ? 'text-stone-900' : ''
        }`}
        onClick={() => {
          onSort(column.sort, active ? (direction === 'asc' ? 'desc' : 'asc') : column.firstDirection);
        }}
      >
        {column.label}
        <Icon aria-hidden="true" className={`size-3.5 ${active ? '' : 'text-stone-300'}`} />
      </button>
    </th>
  );
}

/** Contact icons: present in colour, absent dimmed, always with text for a screen reader. */
export function ContactIcons({ item }: { readonly item: CompanyWorklistItem }): React.JSX.Element {
  const channels: readonly [boolean, string, React.ReactNode][] = [
    [item.hasPhone, 'telefone', <Phone key="p" className="size-3.5" />],
    [item.hasEmail, 'e-mail', <Mail key="e" className="size-3.5" />],
    [item.hasWebsite, 'site', <Globe key="s" className="size-3.5" />],
    [item.hasInstagram, 'Instagram', <AtSign key="i" className="size-3.5" />],
  ];
  const present = channels.filter(([has]) => has).map(([, label]) => label);
  return (
    <span className="inline-flex items-center gap-1.5">
      {channels.map(([has, label, icon]) => (
        <span key={label} aria-hidden="true" className={has ? 'text-stone-600' : 'text-stone-400'}>
          {icon}
        </span>
      ))}
      <span className="sr-only">{present.length === 0 ? 'Sem contato' : `Contato: ${present.join(', ')}`}</span>
    </span>
  );
}

function Row({
  item,
  checked,
  previewed,
  onToggle,
  onPreview,
}: {
  readonly item: CompanyWorklistItem;
  readonly checked: boolean;
  readonly previewed: boolean;
  readonly onToggle: () => void;
  readonly onPreview: () => void;
}): React.JSX.Element {
  const lead = item.currentLead;
  return (
    <tr
      className={`cursor-pointer ${previewed ? 'bg-brand-50/60' : checked ? 'bg-stone-50' : 'hover:bg-stone-50'}`}
      onClick={onPreview}
    >
      <td className="px-3 py-2">
        <input
          type="checkbox"
          aria-label={`Selecionar ${item.name}`}
          checked={checked}
          onClick={(event) => {
            event.stopPropagation();
          }}
          onChange={onToggle}
        />
      </td>
      <td className="max-w-[18rem] px-3 py-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <button
            type="button"
            aria-expanded={previewed}
            aria-controls="previa-empresa"
            className="block min-w-0 truncate text-left font-semibold text-stone-900 hover:text-brand-700 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={(event) => {
              event.stopPropagation();
              onPreview();
            }}
          >
            {item.name}
          </button>
          {item.possibleDuplicate && (
            <span className="inline-flex shrink-0 items-center gap-0.5 rounded-md bg-amber-50 px-1.5 py-0.5 text-[11px] font-semibold text-amber-800 ring-1 ring-amber-200">
              <Copy aria-hidden="true" className="size-3" />
              Duplicata?
            </span>
          )}
          {item.archived && (
            <span className="shrink-0 rounded-md bg-stone-100 px-1.5 py-0.5 text-[11px] text-stone-600">Arquivada</span>
          )}
        </div>
        <p className="truncate text-xs text-stone-500">
          {item.segment ?? 'Sem segmento'}
          {/* A cidade tem coluna própria a partir do tablet; antes disso, vem aqui. */}
          <span className="md:hidden">{` · ${item.city}/${item.state}`}</span>
        </p>
      </td>
      <td className="px-3 py-2">
        {lead === undefined ? (
          <span className="text-stone-400">Sem lead</span>
        ) : (
          <div className="flex flex-col items-start gap-0.5">
            <span className="flex items-center gap-1.5">
              <StatusBadge status={lead.status} />
              {!lead.open && <span className="text-[11px] text-stone-500">encerrado</span>}
            </span>
            {lead.open && <span className="text-xs text-stone-500">{lead.assigneeName ?? 'Sem responsável'}</span>}
          </div>
        )}
      </td>
      <td className="hidden px-3 py-2 sm:table-cell">
        <ContactIcons item={item} />
      </td>
      <td className="hidden px-3 py-2 whitespace-nowrap text-stone-700 md:table-cell">{`${item.city}/${item.state}`}</td>
      <td className="hidden px-3 py-2 whitespace-nowrap text-stone-600 lg:table-cell">
        {item.origin === undefined ? '—' : SOURCE_TYPE_LABEL[item.origin]}
      </td>
      <td className="hidden px-3 py-2 whitespace-nowrap text-stone-600 lg:table-cell">
        {item.lastActivityAt === undefined ? (
          <span className="text-stone-400">Nenhuma</span>
        ) : (
          formatDateTime(item.lastActivityAt)
        )}
      </td>
      <td className="hidden px-3 py-2 whitespace-nowrap text-stone-600 xl:table-cell">
        {formatDateTime(item.createdAt)}
      </td>
      <td className="px-3 py-2">
        <Link
          to={`/companies/${item.companyId}`}
          aria-label={`Abrir ${item.name}`}
          className="inline-flex rounded-md p-1 text-stone-400 hover:bg-stone-100 hover:text-stone-700 focus-visible:outline-2 focus-visible:outline-brand-500"
        >
          <ChevronRight aria-hidden="true" className="size-4" />
        </Link>
      </td>
    </tr>
  );
}
