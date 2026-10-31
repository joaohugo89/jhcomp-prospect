import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { PRIORITY_LABEL } from '../../app/labels';
import { Link } from '../../app/router';
import { useLoad } from '../../app/useLoad';
import { PriorityBadge, ScoreIndicator, StatusBadge } from '../../components/Badges';
import { PageHeader } from '../../components/PageHeader';
import { LoadStateView } from '../../components/States';
import { input, link } from '../../components/styles';
import { changeLeadPriority, getLead } from '../../services/leadService';
import { LEAD_PRIORITIES, type Lead } from '../../types/lead';
import { AnalysisSection } from '../analysis/AnalysisSection';
import { DealSection } from '../crm/DealSection';
import { InteractionForm } from '../crm/InteractionForm';
import { TasksSection } from '../crm/TasksSection';
import { ActivityTimeline } from './ActivityTimeline';
import { StatusChangeForm } from './StatusChangeForm';

/**
 * Detalhe do lead (doc 08 §7).
 *
 * Visão geral, presença digital, negócio, interações, tarefas e histórico.
 * Mensagens chegam com o módulo de outreach.
 */
export function LeadDetailPage({ id }: { readonly id: string }): React.JSX.Element {
  const [state, reload] = useLoad(id, (signal) => getLead(id, signal));
  // Lead alterado nesta tela substitui o carregado, sem nova ida à API.
  const [updated, setUpdated] = useState<Lead | null>(null);
  // Interação registrada não altera o lead, mas precisa aparecer no histórico.
  const [interactions, setInteractions] = useState(0);

  if (state.kind !== 'ready') {
    return <LoadStateView state={state} onRetry={reload} notFoundMessage="Lead não encontrado." />;
  }

  const lead = updated !== null && updated.id === state.data.id ? updated : state.data;

  return (
    <>
      <PageHeader
        title={lead.company.name}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span>
              {[lead.company.segment, `${lead.company.city}/${lead.company.state}`]
                .filter((part) => part !== undefined)
                .join(' · ')}
            </span>
            <StatusBadge status={lead.status} />
            <PriorityBadge priority={lead.priority} />
            <span className="text-slate-500">
              Score: <ScoreIndicator score={lead.score} />
            </span>
          </span>
        }
        actions={
          <Link to={`/companies/${lead.company.id}`} className={link}>
            Ver empresa
          </Link>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1fr_2fr]">
        <div className="space-y-6">
          <section aria-labelledby="status" className="rounded-lg border border-slate-200 bg-white p-6">
            <h2 id="status" className="mb-3 text-base font-semibold">
              Status
            </h2>
            <StatusChangeForm lead={lead} onChanged={setUpdated} />
          </section>

          <PrioritySection lead={lead} onChanged={setUpdated} />

          <DealSection lead={lead} />

          <TasksSection leadId={lead.id} />

          <section aria-labelledby="datas" className="rounded-lg border border-slate-200 bg-white p-6 text-sm">
            <h2 id="datas" className="mb-2 text-base font-semibold">
              Datas
            </h2>
            <dl className="grid grid-cols-[8rem_1fr] gap-y-1">
              <dt className="text-slate-500">Aberto em</dt>
              <dd>{formatDateTime(lead.createdAt)}</dd>
              <dt className="text-slate-500">Atualizado em</dt>
              <dd>{formatDateTime(lead.updatedAt)}</dd>
              {lead.closedAt !== undefined && (
                <>
                  <dt className="text-slate-500">Encerrado em</dt>
                  <dd>{formatDateTime(lead.closedAt)}</dd>
                </>
              )}
            </dl>
          </section>
        </div>

        <div className="space-y-6">
          <AnalysisSection
            lead={lead}
            onScoreChanged={() => {
              // O score mudou no servidor: descarta a cópia local e relê o lead.
              setUpdated(null);
              reload();
            }}
          />
          <InteractionForm
            lead={lead}
            onRecorded={(type) => {
              setInteractions((count) => count + 1);
              if (type === 'DO_NOT_CONTACT_REQUEST') {
                // O pedido muda o status no servidor: relê o lead.
                setUpdated(null);
                reload();
              }
            }}
          />
          <ActivityTimeline leadId={lead.id} refreshKey={`${lead.status}:${lead.updatedAt}:${interactions}`} />
        </div>
      </div>
    </>
  );
}

function PrioritySection({
  lead,
  onChanged,
}: {
  readonly lead: Lead;
  readonly onChanged: (lead: Lead) => void;
}): React.JSX.Element {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function change(value: string): Promise<void> {
    const priority = LEAD_PRIORITIES.find((item) => item === value);
    if (priority === undefined || priority === lead.priority) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      onChanged(await changeLeadPriority(lead.id, priority));
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="prioridade" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="prioridade" className="text-base font-semibold">
        Prioridade
      </h2>
      <label className="mt-2 block text-sm font-medium text-slate-700">
        Prioridade do lead
        <select
          className={input}
          value={lead.priority}
          disabled={busy}
          onChange={(event) => {
            void change(event.target.value);
          }}
        >
          {LEAD_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {PRIORITY_LABEL[priority]}
            </option>
          ))}
        </select>
      </label>
      {error !== null && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </section>
  );
}
