import { AlarmClock, Building, FileText, ListTodo, PhoneOutgoing, Users } from 'lucide-react';

import type { CurrentMetrics, PeriodMetrics } from '../model/dashboardData';
import { MetricGrid, type MetricCardProps } from './MetricCard';

/** Key metrics: the period's and the current ones, each number's source in the subtitle. */
export function KeyMetrics({
  period,
  current,
  leadsHref,
}: {
  readonly period: PeriodMetrics;
  readonly current: CurrentMetrics;
  /** The list of leads in a stage, with the dashboard's filters; absent in the preview. */
  readonly leadsHref?: (status: string) => string;
}): React.JSX.Element {
  const link = (href: string): { readonly href?: string } => (leadsHref === undefined ? {} : { href });
  const metricas: readonly MetricCardProps[] = [
    {
      ...countCard('Empresas', period.companiesAdded, 'adicionadas no período', Building, 'brand'),
      ...link('/companies'),
    },
    countCard('Contatos', period.contactsRegistered, 'registrados no período', PhoneOutgoing, 'sky'),
    { ...countCard('Leads ativos', current.activeLeads, 'em aberto, agora', Users, 'indigo'), ...link('/pipeline') },
    {
      ...countCard('Propostas', current.openProposals, 'em aberto, agora', FileText, 'violet'),
      ...link(leadsHref?.('PROPOSAL_SENT') ?? ''),
    },
    { ...countCard('Tarefas', current.openTasks, 'abertas, agora', ListTodo, 'slate'), ...link('#atencao') },
    {
      ...countCard('Atrasadas', current.overdueTasks, 'tarefas com prazo vencido', AlarmClock, 'slate', true),
      ...link('#atencao'),
    },
  ];
  return <MetricGrid title="Métricas-chave" legenda="Período e situação de agora" metricas={metricas} />;
}

function countCard(
  title: string,
  value: number,
  subtitle: string,
  icon: MetricCardProps['icon'],
  tone: MetricCardProps['tone'],
  /** A value above zero needs action (an overdue task, say). */
  alertWhenPositive = false,
): MetricCardProps {
  const state = value === 0 ? 'empty' : alertWhenPositive ? 'alert' : 'normal';
  return { title, value: String(value), subtitle, state, icon, tone };
}
