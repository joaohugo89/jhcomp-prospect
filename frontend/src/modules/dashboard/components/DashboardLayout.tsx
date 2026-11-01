interface DashboardLayoutProps {
  /** Linha sob o título: período. */
  readonly subtitle?: React.ReactNode;
  /** Controles à direita do título (`HeaderFilters`). */
  readonly filters?: React.ReactNode;
  /** O que pede ação agora; vem antes dos números do período. */
  readonly attention?: React.ReactNode;
  /** Bloco de destaque (gráfico de desempenho). */
  readonly hero: React.ReactNode;
  /** Coluna ao lado do destaque: conversão, origem e aviso de dados. */
  readonly highlights: React.ReactNode;
  /** Métricas-chave, em largura total. */
  readonly metrics: React.ReactNode;
  /** Coluna larga de baixo: funil de agora e funil de conversão. */
  readonly funnel: React.ReactNode;
  /** Coluna estreita de baixo: atividade recente e relatório de conversão. */
  readonly activity: React.ReactNode;
}

/**
 * Esqueleto da página, na ordem de leitura do celular: cabeçalho, o que pede
 * atenção, destaque e seus cartões, métricas-chave, funis e atividades.
 *
 * A partir de 1280px é uma grade de 3 colunas: o destaque ocupa duas e os
 * cartões de apoio empilham na terceira; embaixo, os dois funis empilham nas
 * duas largas e o feed fica na estreita. Assim cada coluna tem conteúdo até o
 * fim, sem um bloco curto ao lado de um longo.
 */
export function DashboardLayout(props: DashboardLayoutProps): React.JSX.Element {
  return (
    <div className="space-y-6">
      <DashboardHeader subtitle={props.subtitle} filters={props.filters} />

      {props.attention}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="min-w-0 xl:col-span-2">{props.hero}</div>
        <div className="grid content-start gap-6 md:grid-cols-2 xl:grid-cols-1">{props.highlights}</div>
      </div>

      {props.metrics}

      <div className="grid gap-6 xl:grid-cols-3">
        <div className="min-w-0 space-y-6 xl:col-span-2">{props.funnel}</div>
        <div className="min-w-0">{props.activity}</div>
      </div>
    </div>
  );
}

/** Título, subtítulo e filtros. Também usado sozinho enquanto o resumo carrega. */
export function DashboardHeader({
  subtitle,
  filters,
}: {
  readonly subtitle?: React.ReactNode;
  readonly filters?: React.ReactNode;
}): React.JSX.Element {
  return (
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0">
        <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">Dashboard</h1>
        {subtitle !== undefined && <div className="mt-1 text-xs text-slate-500">{subtitle}</div>}
      </div>
      {filters}
    </header>
  );
}
