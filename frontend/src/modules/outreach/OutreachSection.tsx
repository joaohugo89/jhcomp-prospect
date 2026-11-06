import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { OUTREACH_CHANNEL_LABEL } from '../../app/labels';
import { useLoad } from '../../app/useLoad';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, input } from '../../components/styles';
import { generateMessage, getComposeContext, listMessages } from '../../services/outreachService';
import type { Lead } from '../../types/lead';
import { OUTREACH_CHANNELS, type ChannelOption, type ComposeContext, type OutreachChannel } from '../../types/outreach';
import { MessageCard } from './MessageCard';

const BLOCKED_MESSAGE = 'Esta empresa tem pedido de não contato: nenhuma abordagem pode ser preparada.';

function optionLabel(option: ChannelOption): string {
  const label = OUTREACH_CHANNEL_LABEL[option.channel];
  if (option.blocked) {
    return `${label} — bloqueado`;
  }
  return option.recipient === undefined ? `${label} — sem contato cadastrado` : `${label} — ${option.recipient}`;
}

function usable(option: ChannelOption | undefined): boolean {
  return option !== undefined && !option.blocked && option.recipient !== undefined;
}

/**
 * Mensagens do lead (RF-050 a RF-052, doc 08 §9).
 *
 * A IA só prepara rascunhos com o contexto exibido aqui. Nada é enviado pelo
 * sistema: o operador envia pelo próprio aplicativo e registra o envio.
 */
export function OutreachSection({
  lead,
  onRecorded,
}: {
  readonly lead: Lead;
  /** Envio registrado: a timeline e, se pedido, o status do lead mudaram no servidor. */
  readonly onRecorded: () => void;
}): React.JSX.Element {
  // Mudança de status (ex.: pedido de não contato) altera bloqueios: recarrega.
  const [state, reload] = useLoad(`${lead.id}:${lead.status}:${lead.updatedAt}`, (signal) =>
    Promise.all([getComposeContext(lead.id, signal), listMessages(lead.id, signal)]),
  );
  const [chosen, setChosen] = useState<OutreachChannel | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (state.kind !== 'ready') {
    return (
      <section aria-labelledby="mensagens" className="rounded-2xl border border-stone-200/70 bg-white p-4 shadow-sm">
        <h2 id="mensagens" className="mb-3 text-base font-semibold">
          Mensagens
        </h2>
        <LoadStateView state={state} onRetry={reload} />
      </section>
    );
  }

  const [context, messages] = state.data;
  const channel = chosen ?? context.channels.find(usable)?.channel ?? 'WHATSAPP';
  const selected = context.channels.find((option) => option.channel === channel);
  const anyBlocked = context.channels.some((option) => option.blocked);

  async function generate(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await generateMessage(lead.id, channel);
      reload();
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause, BLOCKED_MESSAGE));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="mensagens" className="rounded-2xl border border-stone-200/70 bg-white p-4 shadow-sm">
      <h2 id="mensagens" className="text-base font-semibold">
        Mensagens
      </h2>
      <p className="mt-1 text-xs text-stone-500">
        A IA só prepara rascunhos. Nenhuma mensagem é enviada pelo sistema: envie pelo seu aplicativo e registre o envio
        aqui.
      </p>

      <ContextSummary context={context} />

      {anyBlocked && (
        <p role="note" className="mt-4 rounded-md bg-red-50 p-3 text-sm text-red-900">
          Há pedido de não contato para esta empresa. Canais bloqueados não podem ser usados.
        </p>
      )}

      <form
        className="mt-4 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          void generate();
        }}
      >
        <label className="min-w-64 flex-1 text-sm font-medium text-stone-700">
          Canal e destinatário
          <select
            className={input}
            value={channel}
            onChange={(event) => {
              setChosen(OUTREACH_CHANNELS.find((item) => item === event.target.value) ?? null);
            }}
          >
            {context.channels.map((option) => (
              <option key={option.channel} value={option.channel} disabled={!usable(option)}>
                {optionLabel(option)}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className={buttonPrimary} disabled={busy || !usable(selected)}>
          {busy ? 'Gerando…' : messages.content.length > 0 ? 'Gerar novo rascunho' : 'Gerar rascunho'}
        </button>
      </form>
      <p aria-live="polite" className="mt-2 text-sm text-red-700">
        {error}
      </p>

      {messages.content.length === 0 ? (
        <p className="mt-4 text-sm text-stone-600">Nenhuma mensagem preparada para este lead.</p>
      ) : (
        <ul className="mt-4 space-y-4">
          {messages.content.map((message) => {
            const option = context.channels.find((item) => item.channel === message.channel);
            return (
              // A chave inclui a versão: texto salvo (já saneado pelo servidor) reinicia o editor.
              <li key={`${message.id}:${message.updatedAt}`}>
                <MessageCard
                  message={message}
                  recipient={option?.recipient}
                  blocked={option?.blocked ?? false}
                  canMarkContacted={lead.allowedTransitions.includes('CONTACTED')}
                  onChanged={reload}
                  onRecorded={() => {
                    reload();
                    onRecorded();
                  }}
                />
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

/** O que a IA recebe e quem assina (doc 08 §9: origem/contexto e identidade do remetente). */
function ContextSummary({ context }: { readonly context: ComposeContext }): React.JSX.Element {
  const place = `${context.city}/${context.state}`;
  return (
    <details className="mt-4 rounded-md border border-stone-200 p-3 text-sm">
      <summary className="cursor-pointer font-medium text-stone-800">Contexto usado na geração</summary>
      <dl className="mt-2 grid grid-cols-[8rem_1fr] gap-y-1">
        <dt className="text-stone-500">Remetente</dt>
        <dd>{`${context.senderName} · ${context.senderOrganization}`}</dd>
        <dt className="text-stone-500">Empresa</dt>
        <dd>{[context.companyName, context.segment, place].filter((part) => part !== undefined).join(' · ')}</dd>
        <dt className="text-stone-500">Achados</dt>
        <dd>
          {context.confirmedFindings.length === 0 ? (
            'Nenhum achado confirmado. O rascunho falará em termos gerais e precisará de revisão.'
          ) : (
            <ul className="list-disc pl-5">
              {context.confirmedFindings.map((finding) => (
                <li key={finding}>{finding}</li>
              ))}
            </ul>
          )}
        </dd>
      </dl>
      <p className="mt-2 text-xs text-stone-500">
        Telefone, e-mail, Instagram e anotações não são enviados ao provedor de IA.
      </p>
    </details>
  );
}
