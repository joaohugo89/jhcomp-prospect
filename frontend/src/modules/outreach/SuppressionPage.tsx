import { useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { IDENTIFIER_TYPE_LABEL, OUTREACH_CHANNEL_LABEL } from '../../app/labels';
import { useLoad } from '../../app/useLoad';
import { DataTable, type Column } from '../../components/DataTable';
import { FormField } from '../../components/FormField';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { buttonPrimary, input } from '../../components/styles';
import { HttpError } from '../../services/httpClient';
import { listSuppressions, registerSuppression } from '../../services/outreachService';
import {
  IDENTIFIER_TYPES,
  OUTREACH_CHANNELS,
  type IdentifierType,
  type OutreachChannel,
  type SuppressionEntry,
} from '../../types/outreach';
import type { Session } from '../../types/session';

const COLUMNS: readonly Column<SuppressionEntry>[] = [
  { header: 'Tipo', render: (entry) => IDENTIFIER_TYPE_LABEL[entry.identifierType] },
  { header: 'Identificador', render: (entry) => <span className="break-all">{entry.identifier}</span> },
  {
    header: 'Canal',
    render: (entry) => (entry.channel === undefined ? 'Todos' : OUTREACH_CHANNEL_LABEL[entry.channel]),
  },
  { header: 'Motivo', render: (entry) => entry.reason, hideOnSmall: true },
  { header: 'Pedido em', render: (entry) => formatDateTime(entry.requestedAt) },
];

const IDENTIFIER_HINT: Readonly<Record<IdentifierType, string>> = {
  PHONE: 'Com DDD, em qualquer formato. Bloqueia WhatsApp e ligação.',
  EMAIL: 'Endereço completo.',
  INSTAGRAM: 'Perfil (@perfil) ou endereço do perfil.',
};

/**
 * Lista de não contato (RF-053, doc 07 §10).
 *
 * Qualquer membro registra: o pedido precisa valer na hora. Só
 * administradores consultam a lista, que reúne contatos de quem pediu para
 * não ser contatado. Não há remoção no MVP (D26).
 */
export function SuppressionPage({ session }: { readonly session: Session }): React.JSX.Element {
  const [refresh, setRefresh] = useState(0);
  return (
    <>
      <PageHeader
        title="Não contato"
        description="Pedidos de quem não quer mais ser abordado. O bloqueio vale para todas as empresas com o mesmo contato e não é removido por importação."
      />
      <div className="space-y-6">
        <RegisterForm
          onRegistered={() => {
            setRefresh((value) => value + 1);
          }}
        />
        {session.role === 'ADMIN' ? (
          <SuppressionList refresh={refresh} />
        ) : (
          <p className="text-sm text-slate-600">Somente administradores consultam a lista de bloqueios.</p>
        )}
      </div>
    </>
  );
}

function RegisterForm({ onRegistered }: { readonly onRegistered: () => void }): React.JSX.Element {
  const [identifierType, setIdentifierType] = useState<IdentifierType>('PHONE');
  const [identifier, setIdentifier] = useState('');
  const [channel, setChannel] = useState<OutreachChannel | undefined>(undefined);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  async function submit(): Promise<void> {
    setBusy(true);
    setFeedback(null);
    setFieldErrors(new Map());
    try {
      await registerSuppression({ identifierType, identifier, channel, reason });
      setIdentifier('');
      setReason('');
      setFeedback('Pedido registrado. Novas abordagens a este contato estão bloqueadas.');
      onRegistered();
    } catch (cause: unknown) {
      const errors = fieldErrorsOf(cause);
      setFieldErrors(errors);
      // Identificador que não normaliza volta como 400 sem campo: o servidor não adivinha o formato.
      const unrecognized = cause instanceof HttpError && cause.type === 'VALIDATION_ERROR' && errors.size === 0;
      setFeedback(
        unrecognized
          ? `Não foi possível reconhecer o ${IDENTIFIER_TYPE_LABEL[identifierType].toLowerCase()} informado.`
          : actionErrorMessage(cause),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="registrar-bloqueio" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="registrar-bloqueio" className="text-base font-semibold">
        Registrar pedido de não contato
      </h2>
      <form
        className="mt-3 grid gap-3 sm:grid-cols-2"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <label className="text-sm font-medium text-slate-700">
          Tipo de contato
          <select
            className={input}
            value={identifierType}
            onChange={(event) => {
              setIdentifierType(IDENTIFIER_TYPES.find((item) => item === event.target.value) ?? 'PHONE');
            }}
          >
            {IDENTIFIER_TYPES.map((item) => (
              <option key={item} value={item}>
                {IDENTIFIER_TYPE_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm font-medium text-slate-700">
          Vale para
          <select
            className={input}
            value={channel ?? ''}
            onChange={(event) => {
              setChannel(OUTREACH_CHANNELS.find((item) => item === event.target.value));
            }}
          >
            <option value="">Todos os canais</option>
            {OUTREACH_CHANNELS.map((item) => (
              <option key={item} value={item}>
                {OUTREACH_CHANNEL_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <div className="sm:col-span-2">
          <FormField
            label="Contato"
            value={identifier}
            onChange={setIdentifier}
            required
            maxLength={320}
            hint={IDENTIFIER_HINT[identifierType]}
            error={fieldErrors.get('identifier')}
          />
        </div>
        <div className="sm:col-span-2">
          <FormField
            label="Motivo"
            value={reason}
            onChange={setReason}
            required
            maxLength={500}
            error={fieldErrors.get('reason')}
          />
        </div>
        <div className="sm:col-span-2">
          <button
            type="submit"
            className={buttonPrimary}
            disabled={busy || identifier.trim() === '' || reason.trim() === ''}
          >
            {busy ? 'Registrando…' : 'Registrar pedido'}
          </button>
          <p aria-live="polite" className="mt-2 text-sm text-slate-700">
            {feedback}
          </p>
        </div>
      </form>
    </section>
  );
}

function SuppressionList({ refresh }: { readonly refresh: number }): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${page}:${refresh}`, (signal) => listSuppressions(page, signal));

  if (state.kind !== 'ready') {
    return <LoadStateView state={state} onRetry={reload} />;
  }
  if (state.data.content.length === 0) {
    return <p className="text-sm text-slate-600">Nenhum pedido de não contato registrado.</p>;
  }
  return (
    <>
      <DataTable
        caption="Pedidos de não contato"
        columns={COLUMNS}
        rows={state.data.content}
        rowKey={(row) => row.id}
      />
      <Pagination page={state.data} onChange={setPage} />
    </>
  );
}
