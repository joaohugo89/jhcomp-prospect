import { ChartColumn, ChevronDown } from 'lucide-react';
import { useState } from 'react';

import { card } from '../../../components/styles';
import type { Conversao, GrupoConversao } from '../model/dashboardData';

/**
 * Relatório de conversão por origem e segmento (RF-071), recolhido por padrão.
 * O denominador fica sempre à vista; percentual só com amostra suficiente.
 */
export function ConversionReport({ conversao }: { readonly conversao: Conversao }): React.JSX.Element {
  const [open, setOpen] = useState(false);

  return (
    <div className="space-y-3">
      <button
        type="button"
        aria-expanded={open}
        aria-controls="relatorio-conversao"
        className={`${card} flex w-full items-center justify-center gap-2 px-3 py-3 text-xs font-semibold text-slate-700 hover:bg-slate-50 hover:text-slate-900 focus-visible:outline-2 focus-visible:outline-brand-500`}
        onClick={() => {
          setOpen(!open);
        }}
      >
        <ChartColumn aria-hidden="true" className="size-4 text-brand-600" />
        {open ? 'Recolher relatório de conversão' : 'Expandir relatório de conversão por origem e segmento'}
        <ChevronDown
          aria-hidden="true"
          className={`size-4 text-slate-400 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <section id="relatorio-conversao" aria-label="Relatório de conversão" className={`${card} p-4 sm:p-5`}>
          <p className="text-xs text-slate-500">
            {`Leads abertos no período. Percentual só com ${conversao.amostraMinima} leads ou mais no grupo.`}
          </p>
          {conversao.porOrigem.length === 0 ? (
            <p className="mt-3 text-sm text-slate-600">Nenhum lead aberto no período.</p>
          ) : (
            <div className="mt-4 grid gap-8 lg:grid-cols-2">
              <ConversionTable title="Por origem" grupos={conversao.porOrigem} />
              <ConversionTable title="Por segmento" grupos={conversao.porSegmento} />
            </div>
          )}
        </section>
      )}
    </div>
  );
}

function ConversionTable({
  title,
  grupos,
}: {
  readonly title: string;
  readonly grupos: readonly GrupoConversao[];
}): React.JSX.Element {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-sm">
        <caption className="mb-2 text-left text-xs font-semibold text-slate-700">{title}</caption>
        <thead>
          <tr className="border-b border-slate-200 text-left text-[11px] tracking-wide text-slate-500 uppercase">
            <th scope="col" className="py-2 pr-3 font-medium">
              Grupo
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">
              Leads
            </th>
            <th scope="col" className="py-2 pr-3 text-right font-medium">
              Contatados
            </th>
            <th scope="col" className="py-2 text-right font-medium">
              Ganhos
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {grupos.map((grupo) => (
            <tr key={grupo.chave ?? ''}>
              <th scope="row" className="py-2 pr-3 text-left font-normal text-slate-800">
                {grupo.nome}
                {!grupo.amostraSuficiente && (
                  <span className="mt-1 block w-fit rounded-full bg-slate-100 px-2 py-0.5 text-[11px] whitespace-nowrap text-slate-500 sm:mt-0 sm:ml-2 sm:inline">
                    amostra pequena
                  </span>
                )}
              </th>
              <td className="py-2 pr-3 text-right tabular-nums">{grupo.leads}</td>
              <td className="py-2 pr-3 text-right tabular-nums">{rate(grupo.contatados, grupo)}</td>
              <td className="py-2 text-right tabular-nums">{rate(grupo.ganhos, grupo)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Contagem e, com amostra suficiente, o percentual sobre os leads do grupo. */
function rate(count: number, grupo: GrupoConversao): string {
  if (!grupo.amostraSuficiente || grupo.leads === 0) {
    return String(count);
  }
  return `${count} (${Math.round((count / grupo.leads) * 100)}%)`;
}
