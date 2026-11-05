import { useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { localDateTime, toInstant } from '../../app/format';
import { CHANNEL_LABEL, DIRECTION_LABEL, INTERACTION_TYPE_LABEL } from '../../app/labels';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { FormField } from '../../components/FormField';
import { buttonPrimary, input } from '../../components/styles';
import { recordInteraction } from '../../services/crmService';
import {
  INTERACTION_CHANNELS,
  INTERACTION_DIRECTIONS,
  INTERACTION_TYPES,
  type InteractionChannel,
  type InteractionDirection,
  type InteractionType,
} from '../../types/crm';
import type { Lead } from '../../types/lead';

/** Abordagens: as que o bloqueio de não contato impede quando partem de nós. */
const APPROACHES: ReadonlySet<InteractionType> = new Set(['CONTACT', 'MEETING', 'PROPOSAL']);

const DEFAULT_DIRECTION: Readonly<Record<InteractionType, InteractionDirection>> = {
  CONTACT: 'OUTBOUND',
  REPLY: 'INBOUND',
  MEETING: 'OUTBOUND',
  PROPOSAL: 'OUTBOUND',
  DO_NOT_CONTACT_REQUEST: 'INBOUND',
  NOTE: 'INTERNAL',
};

function pick<T extends string>(options: readonly T[], value: string, fallback: T): T {
  return options.find((option) => option === value) ?? fallback;
}

/**
 * Registro de interação (RF-041, D25).
 *
 * Registrar contato não muda o status do lead: `CONTACTED` continua sendo
 * decisão do operador (regra 6). Pedido de não contato é a exceção e move o
 * lead para "Não contatar" na hora, por isso pede confirmação.
 */
export function InteractionForm({
  lead,
  onRecorded,
}: {
  readonly lead: Lead;
  readonly onRecorded: (type: InteractionType) => void;
}): React.JSX.Element {
  const [type, setType] = useState<InteractionType>('CONTACT');
  const [channel, setChannel] = useState<InteractionChannel>('WHATSAPP');
  const [direction, setDirection] = useState<InteractionDirection>('OUTBOUND');
  const [occurredAt, setOccurredAt] = useState(() => localDateTime(new Date()));
  const [outcome, setOutcome] = useState('');
  const [notes, setNotes] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  // A regra é do servidor (409); aqui só evita o envio que já se sabe recusado.
  const blocked = lead.status === 'DO_NOT_CONTACT' && APPROACHES.has(type) && direction === 'OUTBOUND';

  async function submit(): Promise<void> {
    const instant = toInstant(occurredAt);
    if (instant === undefined) {
      setFieldErrors(new Map([['occurredAt', 'Informe data e hora.']]));
      return;
    }
    setBusy(true);
    setMessage(null);
    setFieldErrors(new Map());
    try {
      await recordInteraction(lead.id, {
        type,
        channel,
        direction,
        occurredAt: instant,
        outcome: outcome.trim(),
        notes: notes.trim(),
      });
      setOutcome('');
      setNotes('');
      setOccurredAt(localDateTime(new Date()));
      setMessage('Interação registrada.');
      onRecorded(type);
    } catch (cause: unknown) {
      setFieldErrors(fieldErrorsOf(cause));
      setMessage(
        actionErrorMessage(cause, 'Esta empresa está marcada como "Não contatar": abordagem não pode ser registrada.'),
      );
    } finally {
      setBusy(false);
      setConfirming(false);
    }
  }

  const dateError = fieldErrors.get('occurredAt');

  return (
    <section aria-labelledby="interacao" className="rounded-2xl border border-slate-200/70 bg-white p-4 shadow-sm">
      <h2 id="interacao" className="text-sm font-semibold text-slate-900">
        Registrar interação
      </h2>
      <p className="mt-1 text-xs text-slate-500">Registrar contato não muda o status do lead.</p>
      <form
        className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (type === 'DO_NOT_CONTACT_REQUEST') {
            setConfirming(true);
            return;
          }
          void submit();
        }}
      >
        <label className="text-sm font-medium text-slate-700">
          Tipo
          <select
            className={input}
            value={type}
            onChange={(event) => {
              const next = pick(INTERACTION_TYPES, event.target.value, 'CONTACT');
              setType(next);
              setDirection(DEFAULT_DIRECTION[next]);
            }}
          >
            {INTERACTION_TYPES.map((item) => (
              <option key={item} value={item}>
                {INTERACTION_TYPE_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm font-medium text-slate-700">
          Canal
          <select
            className={input}
            value={channel}
            onChange={(event) => {
              setChannel(pick(INTERACTION_CHANNELS, event.target.value, 'OTHER'));
            }}
          >
            {INTERACTION_CHANNELS.map((item) => (
              <option key={item} value={item}>
                {CHANNEL_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm font-medium text-slate-700">
          Quem iniciou
          <select
            className={input}
            value={direction}
            onChange={(event) => {
              setDirection(pick(INTERACTION_DIRECTIONS, event.target.value, 'INTERNAL'));
            }}
          >
            {INTERACTION_DIRECTIONS.map((item) => (
              <option key={item} value={item}>
                {DIRECTION_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm font-medium text-slate-700">
          Quando
          <input
            type="datetime-local"
            className={input}
            value={occurredAt}
            required
            aria-invalid={dateError !== undefined}
            onChange={(event) => {
              setOccurredAt(event.target.value);
            }}
          />
          {dateError !== undefined && <span className="mt-1 block text-sm font-normal text-red-700">{dateError}</span>}
        </label>
        <div className="sm:col-span-2">
          <FormField
            label="Resultado"
            value={outcome}
            onChange={setOutcome}
            maxLength={200}
            error={fieldErrors.get('outcome')}
          />
        </div>
        <div className="sm:col-span-2">
          <FormField
            label="Anotações"
            value={notes}
            onChange={setNotes}
            multiline
            maxLength={2000}
            error={fieldErrors.get('notes')}
          />
        </div>

        {blocked && (
          <p role="note" className="rounded-md bg-red-50 p-3 text-sm text-red-900 sm:col-span-2 xl:col-span-4">
            Esta empresa está marcada como “Não contatar”. Só é possível registrar o que partiu dela ou uma anotação
            interna.
          </p>
        )}
        {type === 'DO_NOT_CONTACT_REQUEST' && lead.status !== 'DO_NOT_CONTACT' && (
          <p role="note" className="rounded-md bg-amber-50 p-3 text-sm text-amber-950 sm:col-span-2 xl:col-span-4">
            Ao registrar, o lead passa para “Não contatar”.
          </p>
        )}

        <div className="sm:col-span-2 xl:col-span-4">
          <button type="submit" className={buttonPrimary} disabled={busy || blocked}>
            {busy ? 'Registrando…' : 'Registrar interação'}
          </button>
          <p aria-live="polite" className="mt-2 text-sm text-slate-700">
            {message}
          </p>
        </div>
      </form>

      {confirming && (
        <ConfirmDialog
          title="Registrar pedido de não contato?"
          description="A empresa não deve mais ser abordada. O lead passa para “Não contatar”, o que não pode ser desfeito por mudança de status."
          confirmLabel="Registrar pedido"
          destructive
          busy={busy}
          onConfirm={() => {
            void submit();
          }}
          onCancel={() => {
            setConfirming(false);
          }}
        />
      )}
    </section>
  );
}
