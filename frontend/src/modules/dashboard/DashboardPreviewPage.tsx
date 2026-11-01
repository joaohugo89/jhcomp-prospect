import { AttentionPanel } from './components/AttentionPanel';
import { ConversionFunnel } from './components/ConversionFunnel';
import { ConversionGauge } from './components/ConversionGauge';
import { ConversionReport } from './components/ConversionReport';
import { DashboardLayout } from './components/DashboardLayout';
import { DataQualityNotice } from './components/DataQualityNotice';
import { KeyMetrics } from './components/KeyMetrics';
import { LeadSourceSplit } from './components/LeadSourceSplit';
import { PerformanceHero } from './components/PerformanceHero';
import { RecentActivityList } from './components/RecentActivityList';
import { StatusProgressList } from './components/StatusProgressList';
import {
  MOCK_NOW,
  mockAtencao,
  mockAtividadesRecentes,
  mockConversao,
  mockDashboardData,
  mockFunil,
  mockMetricasAnteriores,
  mockSerie,
} from './model/mockDashboardData';

/**
 * O dashboard inteiro com dados simulados, sem API. Só existe em
 * desenvolvimento (`/dashboard/preview`): serve para ajustar o layout sem
 * depender de dados no banco.
 */
export function DashboardPreviewPage(): React.JSX.Element {
  const data = mockDashboardData;
  return (
    <DashboardLayout
      subtitle="Dados simulados · 08/10 a 06/11"
      filters={
        <p className="rounded-lg bg-amber-100 px-3 py-1.5 text-xs font-semibold text-amber-900">Pré-visualização</p>
      }
      attention={<AttentionPanel atencao={mockAtencao} now={MOCK_NOW} />}
      hero={
        <PerformanceHero
          atual={data.metricasPeriodo}
          anterior={mockMetricasAnteriores}
          serie={mockSerie}
          recarregando={false}
          leadsNoPeriodo={mockConversao.total.leads}
          granularidade="WEEK"
          diarioPermitido
          onGranularidade={() => undefined}
        />
      }
      highlights={
        <>
          <ConversionGauge conversao={mockConversao} />
          <LeadSourceSplit conversao={mockConversao} />
          <DataQualityNotice semSegmento={data.empresasSemSegmento} />
        </>
      }
      metrics={<KeyMetrics periodo={data.metricasPeriodo} atuais={data.metricasAtuais} />}
      funnel={
        <>
          <StatusProgressList itens={data.leadsPorStatus} filtrado={false} />
          <ConversionFunnel funil={mockFunil} leadsHref={() => '/leads'} />
        </>
      }
      activity={
        <div className="space-y-3">
          <section aria-labelledby="atividade" className="space-y-3">
            <h2 id="atividade" className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">
              Interações e atividades
            </h2>
            <RecentActivityList atividades={mockAtividadesRecentes} />
          </section>
          <ConversionReport conversao={mockConversao} />
        </div>
      }
    />
  );
}
