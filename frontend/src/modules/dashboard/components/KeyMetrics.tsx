import { AlarmClock, Building, FileText, ListTodo, PhoneOutgoing, Users } from 'lucide-react';

import type { MetricasAtuais, MetricasPeriodo } from '../model/dashboardData';
import { MetricGrid, type MetricCardProps } from './MetricCard';

/** Métricas-chave: as do período e as de agora, com a origem de cada número no subtítulo. */
export function KeyMetrics({
  periodo,
  atuais,
}: {
  readonly periodo: MetricasPeriodo;
  readonly atuais: MetricasAtuais;
}): React.JSX.Element {
  const metricas: readonly MetricCardProps[] = [
    contagem('Empresas', periodo.empresasAdicionadas, 'adicionadas no período', Building, 'brand'),
    contagem('Contatos', periodo.contatosRegistrados, 'registrados no período', PhoneOutgoing, 'sky'),
    contagem('Leads ativos', atuais.leadsAtivos, 'em aberto, agora', Users, 'indigo'),
    contagem('Propostas', atuais.propostasEmAberto, 'em aberto, agora', FileText, 'violet'),
    contagem('Tarefas', atuais.tarefasAbertas, 'abertas, agora', ListTodo, 'slate'),
    {
      ...contagem('Atrasadas', atuais.tarefasAtrasadas, 'tarefas com prazo vencido', AlarmClock, 'slate'),
      ...(atuais.tarefasAtrasadas > 0 ? { estado: 'alerta' as const } : {}),
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
): MetricCardProps {
  return { titulo, valor: String(valor), subtitulo, estado: valor === 0 ? 'vazio' : 'normal', icone, tom };
}
