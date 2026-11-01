interface DashboardLayoutProps {
  /** Linha sob o título: período e variação. */
  readonly subtitle?: React.ReactNode;
  /** Controles à direita do título (`HeaderFilters`). */
  readonly filters?: React.ReactNode;
  /** Bloco de destaque (gráfico de desempenho). */
  readonly hero: React.ReactNode;
  /** Cartões ao lado do destaque em tela larga, abaixo dele nas estreitas. */
  readonly highlights: React.ReactNode;
  /** Métricas-chave, em largura total. */
  readonly metrics: React.ReactNode;
  /** Funil de status. */
  readonly funnel: React.ReactNode;
  /** Atividade recente e relatório de conversão. */
  readonly activity: React.ReactNode;
}

/**
 * Esqueleto da página, na ordem de leitura do celular: cabeçalho, destaque,
 * conversão e origem, métricas-chave, funil e atividades. Em tela larga o
 * destaque ganha os cartões ao lado e o funil fica ao lado das atividades.
 * O menu da aplicação é do `AppShell`; este layout cuida só do conteúdo.
 */
export function DashboardLayout(props: DashboardLayoutProps): React.JSX.Element {
  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">Dashboard</h1>
          {props.subtitle !== undefined && <div className="mt-1 text-xs text-slate-500">{props.subtitle}</div>}
        </div>
        {props.filters}
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="min-w-0">{props.hero}</div>
        <div className="grid grid-cols-1 gap-6 min-[420px]:grid-cols-2 lg:grid-cols-1">{props.highlights}</div>
      </div>

      {props.metrics}

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="min-w-0">{props.funnel}</div>
        <div className="min-w-0">{props.activity}</div>
      </div>
    </div>
  );
}
