import { Link } from '../../../app/router';
import type { Funil } from '../model/dashboardData';
import { DashboardSection } from './DashboardSection';

const percent = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 0 });
const days = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

/**
 * Funil de conversão (RF-071): dos leads abertos no período, quantos
 * alcançaram cada etapa, quanto passou de uma para a seguinte e quanto tempo
 * ficaram nela. Percentual e média só com amostra mínima (D29).
 */
export function ConversionFunnel({
  funil,
  leadsHref,
}: {
  readonly funil: Funil;
  /** Lista de leads hoje na etapa, com os filtros do dashboard. */
  readonly leadsHref: (status: string) => string;
}): React.JSX.Element {
  const { coorte, etapas, amostraMinima } = funil;

  return (
    <DashboardSection
      id="funil-conversao"
      title="Funil de conversão"
      description={`Dos ${coorte} leads abertos no período: até onde chegaram e quanto tempo ficaram em cada etapa.`}
    >
      {coorte === 0 ? (
        <p className="mt-4 text-sm text-slate-600">Nenhum lead aberto no período.</p>
      ) : (
        <>
          <ol className="mt-5 space-y-2.5">
            {etapas.map((etapa) => (
              <li
                key={etapa.status}
                className="grid grid-cols-[7.5rem_1fr] items-center gap-x-3 gap-y-1 text-sm sm:grid-cols-[9rem_1fr_7rem_7.5rem]"
              >
                <Link
                  to={leadsHref(etapa.status)}
                  className="truncate text-slate-700 hover:text-brand-700 hover:underline focus-visible:outline-2 focus-visible:outline-brand-500"
                >
                  {etapa.nome}
                </Link>
                <div className="flex items-center gap-2">
                  <div aria-hidden="true" className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                    <div
                      className="h-full rounded-full bg-brand-600"
                      style={{ width: `${(etapa.alcancados / coorte) * 100}%` }}
                    />
                  </div>
                  <span className="w-8 text-right text-xs font-semibold text-slate-900 tabular-nums">
                    {etapa.alcancados}
                  </span>
                </div>
                <span className="col-start-2 text-xs whitespace-nowrap text-slate-500 tabular-nums sm:col-start-auto sm:text-right">
                  {etapa.passagem === undefined ? '' : `${percent.format(etapa.passagem)} passaram`}
                </span>
                <span className="col-start-2 text-xs whitespace-nowrap text-slate-500 tabular-nums sm:col-start-auto sm:text-right">
                  {etapa.mediaDias === undefined
                    ? etapa.saidas > 0
                      ? 'amostra pequena'
                      : ''
                    : etapa.mediaDias < 1
                      ? 'menos de 1 dia'
                      : `~${days.format(etapa.mediaDias)} dias`}
                </span>
              </li>
            ))}
          </ol>
          <p className="mt-4 border-t border-slate-100 pt-3 text-xs text-slate-500">
            {`Barra: chegaram à etapa ou além. Última coluna: tempo médio na etapa de quem já saiu dela. Percentual e tempo médio só com ${amostraMinima} leads ou mais na base. O nome da etapa abre os leads que estão nela agora.`}
          </p>
        </>
      )}
    </DashboardSection>
  );
}
