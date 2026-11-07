import { input } from './styles';
import type { Segments } from '../types/worklist';

/** The select's value: one segment, no filter, or "no segment". */
export interface SegmentChoice {
  readonly segment: string;
  readonly noSegment: boolean;
}

const NONE = '__sem_segmento__';

/**
 * Segment filter built from the segments in use, with how many companies each
 * one has. Free text demanded the exact spelling and showed nothing of what
 * exists; here you can only pick something that returns results. A segment
 * from the URL that is not in the list — an old link — stays selected.
 */
export function SegmentSelect({
  value,
  onChange,
  segments,
  allowNone,
}: {
  readonly value: SegmentChoice;
  readonly onChange: (value: SegmentChoice) => void;
  /** Absent while loading: the select keeps its current value. */
  readonly segments: Segments | null;
  /** Offers "no segment" (the listing has to know how to filter by it). */
  readonly allowNone: boolean;
}): React.JSX.Element {
  const known = segments?.segments ?? [];
  const current = value.noSegment ? NONE : value.segment;
  const missing =
    value.segment !== '' &&
    !value.noSegment &&
    !known.some((item) => item.name.toLowerCase() === value.segment.toLowerCase());

  return (
    <label className="text-xs font-medium text-stone-500">
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
