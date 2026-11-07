import { useId, useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { formatCurrency, formatDateTime } from '../../app/format';
import { useLoad } from '../../app/useLoad';
import { FormField } from '../../components/FormField';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, card, input } from '../../components/styles';
import { getDeal, updateDeal } from '../../services/crmService';
import type { Deal } from '../../types/crm';
import type { Lead } from '../../types/lead';

const OUTCOME_LABEL: Readonly<Record<Deal['outcome'], string>> = {
  OPEN: 'Em negociação',
  WON: 'Ganho',
  LOST: 'Perdido',
};

/**
 * The lead's deal (D25): service, value and loss reason.
 *
 * The deal appears when the lead reaches the proposal; before that the API
 * answers 404, which here is a normal state rather than an error. Won or lost
 * is the lead's status (D2), which is why there is no outcome field.
 */
export function DealSection({
  lead,
  onChanged,
}: {
  readonly lead: Lead;
  /** The deal was saved: whatever shows the value (the lead summary) re-reads it. */
  readonly onChanged?: () => void;
}): React.JSX.Element {
  // The status is part of the key: reaching the proposal creates the deal on the server.
  const [state, reload] = useLoad(`${lead.id}:${lead.status}`, (signal) => getDeal(lead.id, signal));

  return (
    <section aria-labelledby="negocio" className={`${card} p-4`}>
      <h2 id="negocio" className="text-sm font-semibold text-stone-900">
        Negócio
      </h2>
      {state.kind === 'notFound' ? (
        <p className="mt-2 text-sm text-stone-600">O negócio é criado quando a proposta é enviada.</p>
      ) : state.kind !== 'ready' ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : (
        <DealForm
          key={state.data.updatedAt}
          deal={state.data}
          {...(onChanged === undefined ? {} : { onSaved: onChanged })}
        />
      )}
    </section>
  );
}

function DealForm({
  deal: initial,
  onSaved,
}: {
  readonly deal: Deal;
  readonly onSaved?: () => void;
}): React.JSX.Element {
  const valueId = useId();
  const [deal, setDeal] = useState(initial);
  const [service, setService] = useState(initial.service ?? '');
  const [value, setValue] = useState(initial.estimatedValue === undefined ? '' : String(initial.estimatedValue));
  const [lossReason, setLossReason] = useState(initial.lossReason ?? '');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  async function save(): Promise<void> {
    const estimatedValue = value.trim() === '' ? undefined : Number(value);
    if (estimatedValue !== undefined && (!Number.isFinite(estimatedValue) || estimatedValue < 0)) {
      setFieldErrors(new Map([['estimatedValue', 'Informe um valor não negativo.']]));
      return;
    }
    setBusy(true);
    setMessage(null);
    setFieldErrors(new Map());
    try {
      setDeal(
        await updateDeal(deal.leadId, {
          service: service.trim(),
          estimatedValue,
          lossReason: lossReason.trim(),
        }),
      );
      setMessage('Negócio salvo.');
      onSaved?.();
    } catch (cause: unknown) {
      setFieldErrors(fieldErrorsOf(cause));
      setMessage(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  const valueError = fieldErrors.get('estimatedValue');

  return (
    <form
      className="mt-3 space-y-3"
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
    >
      <p className="text-sm text-stone-700">
        {`Situação: ${OUTCOME_LABEL[deal.outcome]}`}
        {deal.estimatedValue === undefined ? '' : ` · ${formatCurrency(deal.estimatedValue)}`}
      </p>
      <FormField
        label="Serviço"
        value={service}
        onChange={setService}
        maxLength={200}
        error={fieldErrors.get('service')}
      />
      <div>
        <label htmlFor={valueId} className="block text-sm font-medium text-stone-700">
          Valor estimado (R$)
        </label>
        <input
          id={valueId}
          type="number"
          inputMode="decimal"
          min={0}
          step="0.01"
          className={input}
          value={value}
          aria-invalid={valueError !== undefined}
          {...(valueError === undefined ? {} : { 'aria-describedby': `${valueId}-error` })}
          onChange={(event) => {
            setValue(event.target.value);
          }}
        />
        {valueError !== undefined && (
          <p id={`${valueId}-error`} className="mt-1 text-sm text-red-700">
            {valueError}
          </p>
        )}
      </div>
      {(deal.outcome === 'LOST' || deal.lossReason !== undefined) && (
        <FormField
          label="Motivo da perda"
          value={lossReason}
          onChange={setLossReason}
          multiline
          maxLength={500}
          error={fieldErrors.get('lossReason')}
        />
      )}
      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" className={buttonPrimary} disabled={busy}>
          {busy ? 'Salvando…' : 'Salvar negócio'}
        </button>
        <span className="text-xs text-stone-500">{`Atualizado em ${formatDateTime(deal.updatedAt)}`}</span>
      </div>
      <p aria-live="polite" className="text-sm text-stone-700">
        {message}
      </p>
    </form>
  );
}
