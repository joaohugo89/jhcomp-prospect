import type { Conversao } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

const percent = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/** Raio e comprimento do arco de meia-lua, no sistema de coordenadas do SVG. */
const RADIUS = 40;
const ARC = Math.PI * RADIUS;

/**
 * Lead → ganho: dos leads abertos no período, quantos hoje estão ganhos
 * (RF-071, D29). Abaixo da amostra mínima, mostra só as contagens: um
 * percentual com poucos leads engana.
 */
export function ConversionGauge({ conversao }: { readonly conversao: Conversao | undefined }): React.JSX.Element {
  const total = conversao?.total;
  const taxa =
    total !== undefined && total.amostraSuficiente && total.leads > 0 ? total.ganhos / total.leads : undefined;

  return (
    <DashboardSection id="taxa-conversao" title="Taxa de conversão" description="Leads abertos no período → ganhos">
      <div className="relative mx-auto mt-4 w-40">
        <svg aria-hidden="true" viewBox="0 0 100 56" className="w-full overflow-visible">
          {/* Trilho num tom claro da mesma rampa: a meia-lua lê como um medidor só. */}
          <path
            d="M 10 50 A 40 40 0 0 1 90 50"
            fill="none"
            className="stroke-brand-100"
            strokeWidth={9}
            strokeLinecap="round"
          />
          {taxa !== undefined && taxa > 0 && (
            <path
              d="M 10 50 A 40 40 0 0 1 90 50"
              fill="none"
              className="stroke-brand-600"
              strokeWidth={9}
              strokeLinecap="round"
              strokeDasharray={`${ARC * taxa} ${ARC}`}
            />
          )}
        </svg>
        <div className="absolute inset-x-0 bottom-0 text-center">
          <p className="text-2xl leading-none font-bold text-slate-900">
            {taxa === undefined ? '—' : `${percent.format(taxa * 100)}%`}
          </p>
        </div>
      </div>
      <p className="mt-3 border-t border-slate-100 pt-3 text-center text-xs text-slate-500">
        {total === undefined
          ? 'Carregando…'
          : total.leads === 0
            ? 'Nenhum lead aberto no período.'
            : `${total.ganhos} de ${total.leads} ${total.leads === 1 ? 'lead' : 'leads'}${
                taxa === undefined ? ` · amostra pequena (mínimo ${conversao?.amostraMinima ?? 0})` : ''
              }`}
      </p>
    </DashboardSection>
  );
}
