import { Tags } from 'lucide-react';

import { Link } from '../../../app/router';
import { card } from '../../../components/styles';

/**
 * Data quality: without a segment, conversion by segment is incomplete. It
 * only appears when there is something to fix, and fits in the supporting
 * column beside the hero.
 */
export function DataQualityNotice({ semSegmento }: { readonly semSegmento: number }): React.JSX.Element | null {
  if (semSegmento === 0) {
    return null;
  }
  return (
    <aside aria-label="Qualidade dos dados" className={`${card} flex items-start gap-3 p-4 text-sm`}>
      <span aria-hidden="true" className="rounded-lg bg-amber-50 p-1.5 text-amber-700">
        <Tags className="size-4" />
      </span>
      <div className="min-w-0">
        <p className="font-semibold text-stone-900">
          {`${semSegmento} ${semSegmento === 1 ? 'empresa sem segmento' : 'empresas sem segmento'}`}
        </p>
        <p className="mt-0.5 text-xs text-stone-500">A conversão por segmento fica incompleta sem essa informação.</p>
        <Link to="/companies" className="mt-1.5 inline-block text-xs font-semibold text-brand-700 hover:underline">
          Completar em Empresas
        </Link>
      </div>
    </aside>
  );
}
