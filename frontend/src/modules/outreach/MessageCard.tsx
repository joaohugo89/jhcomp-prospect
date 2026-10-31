import { useId, useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { OUTREACH_CHANNEL_LABEL } from '../../app/labels';
import { MessageStatusBadge } from '../../components/Badges';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { FormField } from '../../components/FormField';
import { buttonPrimary, buttonSecondary, input } from '../../components/styles';
import { approveMessage, cancelMessage, editMessage } from '../../services/outreachService';
import { CHANNEL_MAX_LENGTH, type Message, type MessageStatus } from '../../types/outreach';
import { RecordSendForm } from './RecordSendForm';

const BLOCKED_MESSAGE = 'Esta empresa tem pedido de não contato: a mensagem não pode seguir.';

const FINAL: ReadonlySet<MessageStatus> = new Set(['SENT', 'FAILED', 'CANCELLED']);

/**
 * Uma mensagem: revisão, edição, aprovação e registro de envio (RF-051, RF-052).
 *
 * Aprovar exige o texto salvo e, quando a IA marcou afirmações não
 * verificadas, a confirmação de que o operador revisou cada uma.
 */
export function MessageCard({
  message,
  recipient,
  blocked,
  canMarkContacted,
  onChanged,
  onRecorded,
}: {
  readonly message: Message;
  readonly recipient: string | undefined;
  readonly blocked: boolean;
  readonly canMarkContacted: boolean;
  readonly onChanged: () => void;
  readonly onRecorded: () => void;
}): React.JSX.Element {
  const [subject, setSubject] = useState(message.subject ?? '');
  const [body, setBody] = useState(message.body);
  const [reviewed, setReviewed] = useState(false);
  const [confirmingDiscard, setConfirmingDiscard] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());
  const reviewId = useId();

  const final = FINAL.has(message.status);
  const awaitingApproval = message.status === 'DRAFT' || message.status === 'NEEDS_REVIEW';
  const dirty = body !== message.body || subject !== (message.subject ?? '');
  const hasClaims = message.unverifiedClaims.length > 0;
  const maxLength = CHANNEL_MAX_LENGTH[message.channel];

  async function run(action: () => Promise<unknown>, done: string): Promise<void> {
    setBusy(true);
    setFeedback(null);
    setFieldErrors(new Map());
    try {
      await action();
      setFeedback(done);
      onChanged();
    } catch (cause: unknown) {
      setFieldErrors(fieldErrorsOf(cause));
      setFeedback(actionErrorMessage(cause, BLOCKED_MESSAGE));
    } finally {
      setBusy(false);
      setConfirmingDiscard(false);
    }
  }

  return (
    <article
      aria-label={`Mensagem por ${OUTREACH_CHANNEL_LABEL[message.channel]}`}
      className="rounded-md border border-slate-200 p-4"
    >
      <header className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-medium">{OUTREACH_CHANNEL_LABEL[message.channel]}</span>
        <MessageStatusBadge status={message.status} />
        <span className="text-slate-500">{formatDateTime(message.createdAt)}</span>
      </header>
      <p className="mt-1 text-xs text-slate-500">
        {`Destinatário: ${recipient ?? 'sem contato cadastrado'}`}
        {message.generatedBy !== undefined && ` · Gerada por ${message.generatedBy} (${message.promptVersion ?? '—'})`}
      </p>

      {final ? (
        <div className="mt-3 text-sm">
          {message.subject !== undefined && <p className="font-medium">{message.subject}</p>}
          <p className="mt-1 whitespace-pre-wrap text-slate-800">{message.body}</p>
          {message.sentAt !== undefined && (
            <p className="mt-2 text-xs text-slate-500">{`Envio registrado para ${formatDateTime(message.sentAt)}`}</p>
          )}
        </div>
      ) : (
        <form
          className="mt-3 space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            void run(
              () => editMessage(message.id, message.channel === 'EMAIL' ? subject : undefined, body),
              'Alterações salvas.',
            );
          }}
        >
          {message.channel === 'EMAIL' && (
            <FormField
              label="Assunto"
              value={subject}
              onChange={setSubject}
              maxLength={200}
              error={fieldErrors.get('subject')}
            />
          )}
          <label className="block text-sm font-medium text-slate-700">
            Texto
            <textarea
              className={input}
              rows={8}
              value={body}
              maxLength={maxLength}
              required
              aria-invalid={fieldErrors.has('body')}
              onChange={(event) => {
                setBody(event.target.value);
              }}
            />
            <span className="mt-1 block text-xs font-normal text-slate-500">{`${body.length}/${maxLength} caracteres`}</span>
          </label>
          {dirty && (
            <button type="submit" className={buttonSecondary} disabled={busy || body.trim() === ''}>
              Salvar alterações
            </button>
          )}
        </form>
      )}

      {hasClaims && !final && (
        <div role="note" className="mt-3 rounded-md bg-amber-50 p-3 text-sm text-amber-950">
          <p className="font-medium">Afirmações não verificadas</p>
          <ul className="mt-1 list-disc pl-5">
            {message.unverifiedClaims.map((claim) => (
              <li key={claim}>{claim}</li>
            ))}
          </ul>
          {awaitingApproval && (
            <p className="mt-2 flex items-start gap-2">
              <input
                id={reviewId}
                type="checkbox"
                className="mt-1"
                checked={reviewed}
                onChange={(event) => {
                  setReviewed(event.target.checked);
                }}
              />
              <label htmlFor={reviewId}>Revisei estas afirmações; a mensagem só diz o que é verdade.</label>
            </p>
          )}
        </div>
      )}

      {blocked && !final && (
        <p role="note" className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-900">
          Canal bloqueado por pedido de não contato.
        </p>
      )}

      {!final && (
        <div className="mt-3 flex flex-wrap gap-2">
          {awaitingApproval && (
            <button
              type="button"
              className={buttonPrimary}
              disabled={busy || blocked || dirty || (hasClaims && !reviewed)}
              onClick={() => {
                void run(() => approveMessage(message.id, reviewed), 'Mensagem aprovada. Ela ainda não foi enviada.');
              }}
            >
              Aprovar
            </button>
          )}
          <button
            type="button"
            className={buttonSecondary}
            disabled={busy}
            onClick={() => {
              setConfirmingDiscard(true);
            }}
          >
            Descartar
          </button>
        </div>
      )}
      {awaitingApproval && dirty && (
        <p className="mt-2 text-xs text-slate-500">Salve as alterações antes de aprovar.</p>
      )}

      <p aria-live="polite" className="mt-2 text-sm text-slate-700">
        {feedback}
      </p>

      {message.status === 'APPROVED' && !blocked && !dirty && (
        <RecordSendForm message={message} canMarkContacted={canMarkContacted} onRecorded={onRecorded} />
      )}

      {confirmingDiscard && (
        <ConfirmDialog
          title="Descartar mensagem?"
          description="A mensagem fica no histórico como descartada e não pode mais ser editada, aprovada ou registrada como enviada."
          confirmLabel="Descartar"
          destructive
          busy={busy}
          onConfirm={() => {
            void run(() => cancelMessage(message.id), 'Mensagem descartada.');
          }}
          onCancel={() => {
            setConfirmingDiscard(false);
          }}
        />
      )}
    </article>
  );
}
