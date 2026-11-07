interface DashboardLayoutProps {
  /** The line under the title: the period. */
  readonly subtitle?: React.ReactNode;
  /** Controls to the right of the title (`HeaderFilters`). */
  readonly filters?: React.ReactNode;
  /** What needs action now; it comes before the period's numbers. */
  readonly attention?: React.ReactNode;
  /** The hero block (the performance chart). */
  readonly hero: React.ReactNode;
  /** The column beside the hero: conversion, origin and the data notice. */
  readonly highlights: React.ReactNode;
  /** Key metrics, full width. */
  readonly metrics: React.ReactNode;
  /** The wide bottom column: the current funnel and the conversion funnel. */
  readonly funnel: React.ReactNode;
  /** The narrow bottom column: recent activity and the conversion report. */
  readonly activity: React.ReactNode;
}

/**
 * The page skeleton, in the reading order of a phone: header, what needs
 * attention, the hero and its cards, key metrics, funnels and activity.
 *
 * From 1280px up it is a three-column grid: the hero takes two and the
 * supporting cards stack in the third; below, the two funnels stack in the
 * wide pair and the feed sits in the narrow one. That way every column has
 * content all the way down, instead of a short block beside a long one.
 */
export function DashboardLayout(props: DashboardLayoutProps): React.JSX.Element {
  return (
    <div className="space-y-4">
      <DashboardHeader subtitle={props.subtitle} filters={props.filters} />

      {props.attention}

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="min-w-0 xl:col-span-2">{props.hero}</div>
        <div className="grid content-start gap-4 md:grid-cols-2 xl:grid-cols-1">{props.highlights}</div>
      </div>

      {props.metrics}

      <div className="grid gap-4 xl:grid-cols-3">
        <div className="min-w-0 space-y-4 xl:col-span-2">{props.funnel}</div>
        <div className="min-w-0">{props.activity}</div>
      </div>
    </div>
  );
}

/** Title, subtitle and filters. Also used on its own while the summary loads. */
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
        <h1 className="font-display text-2xl font-semibold tracking-tight text-stone-900">Dashboard</h1>
        {subtitle !== undefined && <div className="mt-1 text-xs text-stone-500">{subtitle}</div>}
      </div>
      {filters}
    </header>
  );
}
