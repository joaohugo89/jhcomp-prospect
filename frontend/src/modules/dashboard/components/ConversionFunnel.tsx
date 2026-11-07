import { Link } from '../../../app/router';
import type { FunnelView } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

const percent = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 0 });
const days = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/**
 * Funil de conversão (RF-071): dos leads abertos no período, quantos
 * alcançaram cada stage, quanto passou de uma para a seguinte e quanto tempo
 * ficaram nela. Percentual e média só com amostra mínima (D29).
 */
export function ConversionFunnel({
  funil,
  leadsHref,
}: {
  readonly funil: FunnelView;
  /** Lista de leads hoje na stage, com os filtros do dashboard. */
  readonly leadsHref: (status: string) => string;
}): React.JSX.Element {
  const { cohort, stages, minimumSample } = funil;

  return (
    <DashboardSection
      id="funil-conversao"
      title="Funil de conversão"
      description={`Dos ${cohort} leads abertos no período: até onde chegaram e quanto tempo ficaram em cada stage.`}
    >
      {cohort === 0 ? (
        <p className="mt-4 text-sm text-stone-600">Nenhum lead aberto no período.</p>
      ) : (
        <>
          <ol className="mt-5 space-y-2.5">
            {stages.map((stage) => (
              <li
                key={stage.status}
                className="grid grid-cols-[7.5rem_1fr] items-center gap-x-3 gap-y-1 text-sm sm:grid-cols-[9rem_1fr_7rem_7.5rem]"
              >
                <Link
                  to={leadsHref(stage.status)}
                  className="truncate text-stone-700 hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
                >
                  {stage.label}
                </Link>
                <div className="flex items-center gap-2">
                  <div aria-hidden="true" className="h-2.5 flex-1 overflow-hidden rounded-full bg-stone-100">
                    <div
                      className="h-full rounded-full bg-brand-600"
                      style={{ width: `${(stage.reached / cohort) * 100}%` }}
                    />
                  </div>
                  <span className="w-8 text-right text-xs font-semibold text-stone-900 tabular-nums">
                    {stage.reached}
                  </span>
                </div>
                <span className="col-start-2 text-xs whitespace-nowrap text-stone-500 tabular-nums sm:col-start-auto sm:text-right">
                  {stage.passthrough === undefined ? '' : `${percent.format(stage.passthrough)} passaram`}
                </span>
                <span className="col-start-2 text-xs whitespace-nowrap text-stone-500 tabular-nums sm:col-start-auto sm:text-right">
                  {stage.avgDays === undefined
                    ? stage.exits > 0
                      ? 'amostra pequena'
                      : ''
                    : stage.avgDays < 1
                      ? 'menos de 1 dia'
                      : `~${days.format(stage.avgDays)} dias`}
                </span>
              </li>
            ))}
          </ol>
          <p className="mt-4 border-t border-stone-100 pt-3 text-xs text-stone-500">
            {`Barra: chegaram à stage ou além. Última coluna: tempo médio na stage de quem já saiu dela. Percentual e tempo médio só com ${minimumSample} leads ou mais na base. O nome da stage abre os leads que estão nela agora.`}
          </p>
        </>
      )}
    </DashboardSection>
  );
}
