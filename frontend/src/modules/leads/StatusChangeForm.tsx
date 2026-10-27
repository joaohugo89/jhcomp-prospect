import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { STATUS_LABEL } from '../../app/labels';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { buttonPrimary, input } from '../../components/styles';
import { changeLeadStatus } from '../../services/leadService';
import type { Lead, LeadStatus } from '../../types/lead';

/** Estados sem volta por esta tela: pedem confirmação antes de enviar. */
const IRREVERSIBLE: ReadonlySet<LeadStatus> = new Set(['WON', 'LOST', 'DO_NOT_CONTACT']);

const IRREVERSIBLE_TEXT: Partial<Record<LeadStatus, string>> = {
  WON: 'A oportunidade será encerrada como ganha. Um novo ciclo exige abrir outro lead.',
  LOST: 'A oportunidade será encerrada como perdida. Um novo ciclo exige abrir outro lead.',
  DO_NOT_CONTACT:
    'A empresa não deve mais ser abordada. O bloqueio não pode ser desfeito por mudança de status.',
};

interface StatusChangeFormProps {
  readonly lead: Lead;
  readonly onChanged: (lead: Lead) => void;
}

/**
 * Mudança de status (doc 06 §2).
 *
 * Só oferece as transições que o servidor informou como permitidas; a regra
 * continua no servidor. `CONTACTED` exige que o operador marque que o contato
 * de fato ocorreu: aprovar ou abrir um aplicativo de mensagem não é contato.
 */
export function StatusChangeForm({ lead, onChanged }: StatusChangeFormProps): React.JSX.Element {
  const [target, setTarget] = useState<LeadStatus | ''>('');
  const [reason, setReason] = useState('');
  const [contactConfirmed, setContactConfirmed] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (lead.allowedTransitions.length === 0) {
    return (
      <p className="text-sm text-slate-600">
        Este lead está em um estado final ({STATUS_LABEL[lead.status]}) e não aceita mudança de status.
      </p>
    );
  }

  const needsContactConfirmation = target === 'CONTACTED';
  const canSubmit = target !== '' && (!needsContactConfirmation || contactConfirmed) && !busy;

  async function submit(status: LeadStatus): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      const updated = await changeLeadStatus(lead.id, status, reason, status === 'CONTACTED' && contactConfirmed);
      setTarget('');
      setReason('');
      setContactConfirmed(false);
      setConfirming(false);
      onChanged(updated);
    } catch (cause: unknown) {
      setConfirming(false);
      setError(actionErrorMessage(cause, 'Esta mudança de status não é permitida no estado atual. Recarregue a página.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      className="space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        if (target === '') {
          return;
        }
        if (IRREVERSIBLE.has(target)) {
          setConfirming(true);
          return;
        }
        void submit(target);
      }}
    >
      <label className="block text-sm font-medium text-slate-700">
        Novo status
        <select
          className={input}
          value={target}
          onChange={(event) => {
            setTarget(lead.allowedTransitions.find((status) => status === event.target.value) ?? '');
            setContactConfirmed(false);
          }}
        >
          <option value="">Selecione…</option>
          {lead.allowedTransitions.map((status) => (
            <option key={status} value={status}>
              {STATUS_LABEL[status]}
            </option>
          ))}
        </select>
      </label>

      {needsContactConfirmation && (
        <label className="flex items-start gap-2 rounded-md bg-amber-50 p-3 text-sm text-amber-950">
          <input
            type="checkbox"
            className="mt-0.5"
            checked={contactConfirmed}
            onChange={(event) => {
              setContactConfirmed(event.target.checked);
            }}
          />
          Confirmo que o contato com a empresa foi de fato realizado.
        </label>
      )}

      <label className="block text-sm font-medium text-slate-700">
        Motivo (opcional)
        <textarea
          className={input}
          rows={2}
          maxLength={500}
          value={reason}
          onChange={(event) => {
            setReason(event.target.value);
          }}
        />
      </label>

      <button type="submit" className={buttonPrimary} disabled={!canSubmit}>
        {busy ? 'Salvando…' : 'Alterar status'}
      </button>

      <div aria-live="polite">
        {error !== null && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
      </div>

      {confirming && target !== '' && (
        <ConfirmDialog
          title={`Mudar para "${STATUS_LABEL[target]}"?`}
          description={IRREVERSIBLE_TEXT[target] ?? ''}
          confirmLabel="Confirmar"
          destructive
          busy={busy}
          onConfirm={() => {
            void submit(target);
          }}
          onCancel={() => {
            setConfirming(false);
          }}
        />
      )}
    </form>
  );
}
