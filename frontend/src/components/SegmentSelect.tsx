import { input } from './styles';
import type { Segments } from '../types/worklist';

/** Valor do seletor: um segmento, nenhum filtro, ou "sem segmento". */
export interface SegmentChoice {
  readonly segment: string;
  readonly noSegment: boolean;
}

const NONE = '__sem_segmento__';

/**
 * Filtro por segmento a partir dos segmentos em uso, com quantas empresas em
 * cada. Texto livre exigia a grafia exata e não mostrava o que existe; aqui só
 * se escolhe o que dá resultado. Um segmento da URL que não está na lista
 * (ex.: link antigo) continua selecionado.
 */
export function SegmentSelect({
  value,
  onChange,
  segments,
  allowNone,
}: {
  readonly value: SegmentChoice;
  readonly onChange: (value: SegmentChoice) => void;
  /** Ausente enquanto carrega: o seletor fica com o valor atual. */
  readonly segments: Segments | null;
  /** Oferece "Sem segmento" (a listagem precisa saber filtrar por isso). */
  readonly allowNone: boolean;
}): React.JSX.Element {
  const known = segments?.segments ?? [];
  const current = value.noSegment ? NONE : value.segment;
  const missing =
    value.segment !== '' &&
    !value.noSegment &&
    !known.some((item) => item.name.toLowerCase() === value.segment.toLowerCase());

  return (
    <label className="text-xs font-medium text-slate-500">
      Segmento
      <select
        className={input}
        value={current}
        onChange={(event) => {
          const next = event.target.value;
          onChange(next === NONE ? { segment: '', noSegment: true } : { segment: next, noSegment: false });
        }}
      >
        <option value="">Todos</option>
        {allowNone && (value.noSegment || (segments?.withoutSegment ?? 0) > 0) && (
          <option value={NONE}>
            {segments === null ? 'Sem segmento' : `Sem segmento (${segments.withoutSegment})`}
          </option>
        )}
        {missing && <option value={value.segment}>{value.segment}</option>}
        {known.map((item) => (
          <option key={item.name} value={item.name}>
            {`${item.name} (${item.companies})`}
          </option>
        ))}
      </select>
    </label>
  );
}
