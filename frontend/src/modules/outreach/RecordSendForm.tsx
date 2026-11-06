import { useId, useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { localDateTime, toInstant } from '../../app/format';
import { FormField } from '../../components/FormField';
import { buttonPrimary, input } from '../../components/styles';
import { recordSend } from '../../services/outreachService';
import type { Message } from '../../types/outreach';

/**
 * Registro de um envio que o operador já fez (RF-052, regra 5).
 *
 * Abrir o aplicativo de mensagens não é envio, e por isso não há botão que
 * abra um. O lead só vai para "Contatado" se o operador marcar (regra 6).
 */
export function RecordSendForm({
  message,
  canMarkContacted,
  onRecorded,
}: {
  readonly message: Message;
  readonly canMarkContacted: boolean;
  readonly onRecorded: () => void;
}): React.JSX.Element {
  const [result, setResult] = useState<'SENT' | 'FAILED'>('SENT');
  const [sentAt, setSentAt] = useState(() => localDateTime(new Date()));
  const [notes, setNotes] = useState('');
  const [markContacted, setMarkContacted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | undefined>(undefined);
  const markId = useId();

  async function submit(): Promise<void> {
    const instant = toInstant(sentAt);
    if (instant === undefined) {
      setDateError('Informe data e hora.');
      return;
    }
    setBusy(true);
    setFeedback(null);
    setDateError(undefined);
    try {
      await recordSend(message.id, {
        result,
        sentAt: instant,
        notes,
        markLeadContacted: result === 'SENT' && markContacted,
      });
      onRecorded();
    } catch (cause: unknown) {
      setDateError(fieldErrorsOf(cause).get('sentAt'));
      setFeedback(
        actionErrorMessage(cause, 'O envio não pôde ser registrado: a empresa pode ter pedido para não ser contatada.'),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      aria-label="Registrar envio"
      className="mt-4 space-y-3 rounded-md border border-sky-200 bg-sky-50 p-3"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <p className="text-sm text-sky-950">
        Aprovada não é enviada. Envie o texto pelo seu aplicativo e registre aqui o que aconteceu.
      </p>
      <fieldset>
        <legend className="text-sm font-medium text-stone-700">Resultado</legend>
        <div className="mt-1 flex flex-wrap gap-4 text-sm">
          <label className="flex items-center gap-2">
            <input
              type="radio"
              name={`result-${message.id}`}
              checked={result === 'SENT'}
              onChange={() => {
                setResult('SENT');
              }}
            />
            Enviei a mensagem
          </label>
          <label className="flex items-center gap-2">
            <input
              type="radio"
              name={`result-${message.id}`}
              checked={result === 'FAILED'}
              onChange={() => {
                setResult('FAILED');
                setMarkContacted(false);
              }}
            />
            Não consegui enviar
          </label>
        </div>
      </fieldset>
      <label className="block text-sm font-medium text-stone-700">
        Quando
        <input
          type="datetime-local"
          className={input}
          value={sentAt}
          required
          aria-invalid={dateError !== undefined}
          onChange={(event) => {
            setSentAt(event.target.value);
          }}
        />
        {dateError !== undefined && <span className="mt-1 block text-sm font-normal text-red-700">{dateError}</span>}
      </label>
      <FormField label="Anotações" value={notes} onChange={setNotes} multiline maxLength={2000} />
      {result === 'SENT' && canMarkContacted && (
        <p className="flex items-start gap-2 text-sm">
          <input
            id={markId}
            type="checkbox"
            className="mt-1"
            checked={markContacted}
            onChange={(event) => {
              setMarkContacted(event.target.checked);
            }}
          />
          <label htmlFor={markId}>Mover o lead para “Contatado”</label>
        </p>
      )}
      <button type="submit" className={buttonPrimary} disabled={busy}>
        {busy ? 'Registrando…' : 'Registrar envio'}
      </button>
      <p aria-live="polite" className="text-sm text-red-700">
        {feedback}
      </p>
    </form>
  );
}
