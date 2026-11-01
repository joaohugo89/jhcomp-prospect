import { AlarmClock, Building, FileText, ListTodo, PhoneOutgoing, Users } from 'lucide-react';

import type { MetricasAtuais, MetricasPeriodo } from '../model/dashboardData';
import { MetricGrid, type MetricCardProps } from './MetricCard';

/** Métricas-chave: as do período e as de agora, com a origem de cada número no subtítulo. */
export function KeyMetrics({
  periodo,
  atuais,
  leadsHref,
}: {
  readonly periodo: MetricasPeriodo;
  readonly atuais: MetricasAtuais;
  /** Lista de leads numa etapa, com os filtros do dashboard; ausente na pré-visualização. */
  readonly leadsHref?: (status: string) => string;
}): React.JSX.Element {
  const link = (href: string): { readonly href?: string } => (leadsHref === undefined ? {} : { href });
  const metricas: readonly MetricCardProps[] = [
    {
      ...contagem('Empresas', periodo.empresasAdicionadas, 'adicionadas no período', Building, 'brand'),
      ...link('/companies'),
    },
    contagem('Contatos', periodo.contatosRegistrados, 'registrados no período', PhoneOutgoing, 'sky'),
    { ...contagem('Leads ativos', atuais.leadsAtivos, 'em aberto, agora', Users, 'indigo'), ...link('/pipeline') },
    {
      ...contagem('Propostas', atuais.propostasEmAberto, 'em aberto, agora', FileText, 'violet'),
      ...link(leadsHref?.('PROPOSAL_SENT') ?? ''),
    },
    { ...contagem('Tarefas', atuais.tarefasAbertas, 'abertas, agora', ListTodo, 'slate'), ...link('#atencao') },
    {
      ...contagem('Atrasadas', atuais.tarefasAtrasadas, 'tarefas com prazo vencido', AlarmClock, 'slate', true),
      ...link('#atencao'),
    },
  ];
  return <MetricGrid titulo="Métricas-chave" legenda="Período e situação de agora" metricas={metricas} />;
}

function contagem(
  titulo: string,
  valor: number,
  subtitulo: string,
  icone: MetricCardProps['icone'],
  tom: MetricCardProps['tom'],
  /** Valor acima de zero pede ação (ex.: tarefa atrasada). */
  alertaSeHouver = false,
): MetricCardProps {
  const estado = valor === 0 ? 'vazio' : alertaSeHouver ? 'alerta' : 'normal';
  return { titulo, valor: String(valor), subtitulo, estado, icone, tom };
}
