import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { PRIORITY_LABEL } from '../../app/labels';
import { Link, navigate } from '../../app/router';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { buttonPrimary, buttonSecondary, card, input, link } from '../../components/styles';
import { archiveCompany } from '../../services/companyService';
import { createLead } from '../../services/leadService';
import type { Company } from '../../types/company';
import { LEAD_PRIORITIES, type LeadPriority } from '../../types/lead';

export function ArchiveAction({
  company,
  onArchived,
}: {
  readonly company: Company;
  readonly onArchived: () => void;
}): React.JSX.Element {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function archive(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await archiveCompany(company.id);
      setConfirming(false);
      onArchived();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
      setConfirming(false);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className={buttonSecondary}
        onClick={() => {
          setConfirming(true);
        }}
      >
        Arquivar
      </button>
      {error !== null && (
        <p role="alert" className="w-full text-sm text-red-700">
          {error}
        </p>
      )}
      {confirming && (
        <ConfirmDialog
          title="Arquivar empresa?"
          description={`${company.name} sai da listagem padrão e deixa de aceitar alterações e novas oportunidades. O histórico é preservado.`}
          confirmLabel="Arquivar"
          destructive
          busy={busy}
          onConfirm={() => {
            void archive();
          }}
          onCancel={() => {
            setConfirming(false);
          }}
        />
      )}
    </>
  );
}

export function OpenLeadSection({ companyId }: { readonly companyId: string }): React.JSX.Element {
  const [priority, setPriority] = useState<LeadPriority>('MEDIUM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function open(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const lead = await createLead(companyId, priority);
      navigate(`/leads/${lead.id}`);
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, 'Esta empresa já tem uma oportunidade aberta.'));
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="oportunidade" className={`${card} p-4`}>
      <h2 id="oportunidade" className="text-sm font-semibold text-slate-900">
        Oportunidade
      </h2>
      <p className="mt-1 text-sm text-slate-600">Abre um lead para acompanhar a intenção comercial.</p>
      <form
        className="mt-3 space-y-3"
        onSubmit={(event) => {
          void open(event);
        }}
      >
        <label className="block text-sm font-medium text-slate-700">
          Prioridade
          <select
            className={input}
            value={priority}
            onChange={(event) => {
              const value = LEAD_PRIORITIES.find((item) => item === event.target.value);
              if (value !== undefined) {
                setPriority(value);
              }
            }}
          >
            {LEAD_PRIORITIES.map((item) => (
              <option key={item} value={item}>
                {PRIORITY_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className={buttonPrimary} disabled={busy}>
          {busy ? 'Abrindo…' : 'Abrir lead'}
        </button>
        {error !== null && (
          <p role="alert" className="text-sm text-red-700">
            {error}{' '}
            <Link to="/leads" className={link}>
              Ver leads
            </Link>
          </p>
        )}
      </form>
    </section>
  );
}
