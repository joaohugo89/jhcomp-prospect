import { TriangleAlert, type LucideIcon } from 'lucide-react';

import { Link } from '../../../app/router';
import { card } from '../../../components/styles';

export type Tom = 'brand' | 'sky' | 'indigo' | 'violet' | 'emerald' | 'slate';

/**
 * `vazio`: zero, atenuado para não competir com os números que importam.
 * `alerta`: pede ação; sinalizado por texto e ícone, não só por cor.
 */
export type EstadoMetrica = 'normal' | 'vazio' | 'alerta';

export interface MetricCardProps {
  readonly titulo: string;
  /** Já formatado (contagem ou moeda). */
  readonly valor: string;
  /** Como o número é obtido, curto: a metodologia fica à vista. */
  readonly subtitulo: string;
  readonly estado: EstadoMetrica;
  readonly icone: LucideIcon;
  readonly tom: Tom;
  /** Drill-down: o cartão inteiro leva à lista por trás do número. */
  readonly href?: string;
}

const ICON_TONE: Readonly<Record<Tom | 'amber', string>> = {
  brand: 'bg-brand-50 text-brand-700',
  sky: 'bg-sky-50 text-sky-700',
  indigo: 'bg-indigo-50 text-indigo-700',
  violet: 'bg-violet-50 text-violet-700',
  emerald: 'bg-emerald-50 text-emerald-700',
  slate: 'bg-stone-100 text-stone-600',
  amber: 'bg-amber-50 text-amber-700',
};

/** Grade de indicadores: 2 colunas no celular, 3 no tablet, 6 em tela larga. */
export function MetricGrid({
  titulo,
  legenda,
  metricas,
}: {
  readonly titulo: string;
  readonly legenda?: string;
  readonly metricas: readonly MetricCardProps[];
}): React.JSX.Element {
  return (
    <section aria-label={titulo} className="space-y-3">
      <div className="flex items-baseline justify-between gap-2">
        <h2 className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase">{titulo}</h2>
        {legenda !== undefined && <p className="text-xs text-stone-400">{legenda}</p>}
      </div>
      <dl className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        {metricas.map((metrica) => (
          <MetricCard key={metrica.titulo} {...metrica} />
        ))}
      </dl>
    </section>
  );
}

/** Um indicador: título e ícone, valor e subtítulo. Vive dentro de um `<dl>` (`MetricGrid`). */
export function MetricCard({
  titulo,
  valor,
  subtitulo,
  estado,
  icone: Icon,
  tom,
  href,
}: MetricCardProps): React.JSX.Element {
  const alerta = estado === 'alerta';
  return (
    <div
      className={`${card} relative flex flex-col p-4 ${alerta ? 'border-amber-300 ring-1 ring-amber-200' : ''} ${
        href === undefined ? '' : 'transition-shadow focus-within:ring-2 focus-within:ring-brand-500 hover:shadow-md'
      }`}
    >
      <dt className="pr-9 text-xs font-medium text-stone-500">
        {href === undefined ? (
          titulo
        ) : (
          // Link "esticado": o pseudo-elemento cobre o cartão, e o nome acessível é o título.
          <Link to={href} className="outline-none after:absolute after:inset-0 after:content-['']">
            {titulo}
          </Link>
        )}
      </dt>
      {/* Número isolado em figuras proporcionais; tabular só em colunas. */}
      <dd
        className={`mt-2 font-display text-2xl font-semibold tracking-tight ${estado === 'vazio' ? 'text-stone-300' : 'text-stone-900'}`}
      >
        {valor}
      </dd>
      <dd className="mt-1 flex flex-wrap items-center gap-1.5 text-[11px] text-stone-500">
        {alerta && (
          <span className="inline-flex items-center gap-1 rounded-md bg-amber-100 px-1.5 py-0.5 font-semibold text-amber-800">
            <TriangleAlert aria-hidden="true" className="size-3" />
            Atenção
          </span>
        )}
        {subtitulo}
      </dd>
      <dd
        aria-hidden="true"
        className={`absolute top-3.5 right-3.5 rounded-lg p-1.5 ${ICON_TONE[alerta ? 'amber' : tom]}`}
      >
        <Icon className="size-3.5" strokeWidth={2} />
      </dd>
    </div>
  );
}
