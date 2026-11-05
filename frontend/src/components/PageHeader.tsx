interface PageHeaderProps {
  readonly title: string;
  readonly description?: React.ReactNode;
  readonly actions?: React.ReactNode;
}

/** Título da página. O `h1` é único por tela, para navegação por cabeçalhos. */
export function PageHeader({ title, description, actions }: PageHeaderProps): React.JSX.Element {
  return (
    <header className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-xl font-bold tracking-tight break-words text-slate-900">{title}</h1>
        {description !== undefined && <div className="mt-0.5 text-sm text-slate-500">{description}</div>}
      </div>
      {actions !== undefined && <div className="flex flex-wrap gap-2">{actions}</div>}
    </header>
  );
}
