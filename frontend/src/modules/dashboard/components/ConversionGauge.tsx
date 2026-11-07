import type { ConversionView } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/** Radius and length of the half-moon arc, in the SVG coordinate system. */
const RADIUS = 40;
const ARC = Math.PI * RADIUS;

/**
 * Lead → won: of the leads opened in the period, how many are won today
 * (RF-071, D29). Below the minimum sample it shows counts only: a percentage
 * over a handful of leads misleads. The gauge sits beside the numbers so the
 * card is only as tall as what it says.
 */
export function ConversionGauge({
  conversion,
}: {
  readonly conversion: ConversionView | undefined;
}): React.JSX.Element {
  const total = conversion?.total;
  const taxa = total !== undefined && total.sufficientSample && total.leads > 0 ? total.won / total.leads : undefined;

  return (
    <DashboardSection id="taxa-conversion" title="Taxa de conversão" description="Leads abertos no período → ganhos">
      <div className="mt-4 flex items-center gap-5">
        <svg aria-hidden="true" viewBox="0 0 100 56" className="w-28 shrink-0 overflow-visible">
          {/* Trilho num tone claro da mesma rampa: a meia-lua lê como um medidor só. */}
          <path
            d="M 10 50 A 40 40 0 0 1 90 50"
            fill="none"
            className="stroke-brand-100"
            strokeWidth={10}
            strokeLinecap="round"
          />
          {taxa !== undefined && taxa > 0 && (
            <path
              d="M 10 50 A 40 40 0 0 1 90 50"
              fill="none"
              className="stroke-brand-700"
              strokeWidth={10}
              strokeLinecap="round"
              strokeDasharray={`${ARC * taxa} ${ARC}`}
            />
          )}
        </svg>
        <div className="min-w-0">
          <p className="text-3xl leading-none font-bold tracking-tight text-stone-900">
            {taxa === undefined ? '—' : `${percent.format(taxa * 100)}%`}
          </p>
          <p className="mt-1.5 text-xs text-stone-500">
            {total === undefined
              ? 'Carregando…'
              : total.leads === 0
                ? 'Nenhum lead aberto no período.'
                : `${total.won} de ${total.leads} ${total.leads === 1 ? 'lead' : 'leads'}${
                    taxa === undefined ? ` · amostra pequena (mínimo ${conversion?.minimumSample ?? 0})` : ''
                  }`}
          </p>
        </div>
      </div>
    </DashboardSection>
  );
}
