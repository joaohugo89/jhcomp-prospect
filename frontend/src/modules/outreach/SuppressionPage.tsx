import { AtSign, Mail, Phone, Search, ShieldAlert, ShieldCheck, ShieldQuestion } from 'lucide-react';
import { useId, useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { IDENTIFIER_TYPE_LABEL, OUTREACH_CHANNEL_LABEL } from '../../app/labels';
import { useLastReady, useLoad } from '../../app/useLoad';
import { FormField } from '../../components/FormField';
import { PageHeader } from '../../components/PageHeader';
import { StatTile } from '../../components/StatTile';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, buttonSecondary, card, input } from '../../components/styles';
import { HttpError } from '../../services/httpClient';
import {
  checkSuppression,
  getSuppressionSummary,
  listSuppressions,
  registerSuppression,
} from '../../services/outreachService';
import {
  IDENTIFIER_TYPES,
  OUTREACH_CHANNELS,
  type IdentifierType,
  type OutreachChannel,
  type SuppressionCheck,
  type SuppressionEntry,
} from '../../types/outreach';
import type { Session } from '../../types/session';

const TYPE_ICON: Readonly<Record<IdentifierType, React.ReactNode>> = {
  PHONE: <Phone className="size-4" />,
  EMAIL: <Mail className="size-4" />,
  INSTAGRAM: <AtSign className="size-4" />,
};

const PLACEHOLDER: Readonly<Record<IdentifierType, string>> = {
  PHONE: '(35) 98888-0000',
  EMAIL: 'contato@empresa.com.br',
  INSTAGRAM: '@perfil',
};

const IDENTIFIER_HINT: Readonly<Record<IdentifierType, string>> = {
  PHONE: 'Com DDD, em qualquer formato. Bloqueia WhatsApp e ligação.',
  EMAIL: 'Endereço completo.',
  INSTAGRAM: 'Perfil (@perfil) ou endereço do perfil.',
};

/** Telefone normalizado (+55DDNNNNNNNNN) mostrado como se lê; outro formato fica como está. */
function readable(entry: SuppressionEntry): string {
  const match = entry.identifierType === 'PHONE' ? /^\+55(\d{2})(\d{4,5})(\d{4})$/.exec(entry.identifier) : null;
  return match === null ? entry.identifier : `+55 (${match[1] ?? ''}) ${match[2] ?? ''}-${match[3] ?? ''}`;
}

/** Motivos frequentes: um clique preenche, e o texto continua editável. */
const QUICK_REASONS = ['Pediu por telefone', 'Respondeu pedindo para parar', 'Pedido por e-mail'] as const;

/**
 * Lista de não contato (RF-053, doc 07 §10, D26).
 *
 * Qualquer membro registra (o pedido precisa valer na hora) e confere se um
 * contato está bloqueado antes de abordar por fora do sistema. Só
 * administradores consultam a lista, que reúne contatos de quem pediu para
 * não ser contatado. Não há remoção no MVP.
 */
export function SuppressionPage({ session }: { readonly session: Session }): React.JSX.Element {
  const [refresh, setRefresh] = useState(0);
  const admin = session.role === 'ADMIN';
  return (
    <>
      <PageHeader
        title="Não contato"
        description="Pedidos de quem não quer mais ser abordado. O bloqueio vale para todas as empresas com o mesmo contato e não é removido por importação."
      />
      <div className={`grid gap-4 ${admin ? 'lg:grid-cols-[minmax(0,1fr)_24rem]' : 'lg:grid-cols-2'}`}>
        <div className="min-w-0 space-y-4">
          {admin ? (
            <SuppressionList refresh={refresh} />
          ) : (
            <>
              <CheckCard />
              <p className="rounded-xl bg-stone-100 px-4 py-3 text-sm text-stone-600">
                Somente administradores consultam a lista de bloqueios. Para saber se um contato está bloqueado, use a
                conferência acima.
              </p>
            </>
          )}
        </div>
        <div className="space-y-4">
          {admin && <CheckCard />}
          <RegisterForm
            onRegistered={() => {
              setRefresh((value) => value + 1);
            }}
          />
        </div>
      </div>
    </>
  );
}

/** Escolha do tipo de contato como botões de rádio, com ícone e texto. */
function TypeChoice({
  value,
  onChange,
  legend,
}: {
  readonly value: IdentifierType;
  readonly onChange: (type: IdentifierType) => void;
  readonly legend: string;
}): React.JSX.Element {
  const name = useId();
  return (
    <fieldset>
      <legend className="text-sm font-medium text-stone-700">{legend}</legend>
      <div className="mt-1 grid grid-cols-3 gap-1 rounded-xl bg-stone-100 p-1">
        {IDENTIFIER_TYPES.map((type) => (
          <label
            key={type}
            className={`flex cursor-pointer items-center justify-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold focus-within:outline-2 focus-within:outline-brand-500 ${
              value === type ? 'glass text-stone-900 shadow-sm' : 'text-stone-600 hover:text-stone-900'
            }`}
          >
            <input
              type="radio"
              name={name}
              value={type}
              checked={value === type}
              className="sr-only"
              onChange={() => {
                onChange(type);
              }}
            />
            <span aria-hidden="true">{TYPE_ICON[type]}</span>
            {IDENTIFIER_TYPE_LABEL[type]}
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function CheckCard(): React.JSX.Element {
  const [type, setType] = useState<IdentifierType>('PHONE');
  const [identifier, setIdentifier] = useState('');
  const [result, setResult] = useState<SuppressionCheck | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function check(): Promise<void> {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      setResult(await checkSuppression(type, identifier));
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="conferir-contato" className={`${card} space-y-3 p-4`}>
      <div>
        <h2 id="conferir-contato" className="text-sm font-semibold text-stone-900">
          Conferir um contato
        </h2>
        <p className="mt-1 text-xs text-stone-500">
          Antes de ligar ou escrever por fora do sistema. Mostra só se há bloqueio, sem o motivo.
        </p>
      </div>
      <form
        className="space-y-3"
        onSubmit={(event) => {
          event.preventDefault();
          void check();
        }}
      >
        <TypeChoice
          legend="Tipo do contato a conferir"
          value={type}
          onChange={(next) => {
            setType(next);
            setResult(null);
          }}
        />
        <div className="flex gap-2">
          <label className="min-w-0 flex-1">
            <span className="sr-only">Contato a conferir</span>
            <input
              className={`${input} mt-0`}
              placeholder={PLACEHOLDER[type]}
              maxLength={320}
              value={identifier}
              onChange={(event) => {
                setIdentifier(event.target.value);
                setResult(null);
              }}
            />
          </label>
          <button
            type="submit"
            className={`${buttonSecondary} shrink-0 gap-1.5`}
            disabled={busy || identifier.trim() === ''}
          >
            <Search aria-hidden="true" className="size-4" />
            Conferir
          </button>
        </div>
      </form>
      <div aria-live="polite">
        {error !== null && (
          <p role="alert" className="text-sm text-red-700">
            {error}
          </p>
        )}
        {result !== null && <CheckResult result={result} type={type} />}
      </div>
    </section>
  );
}

function CheckResult({
  result,
  type,
}: {
  readonly result: SuppressionCheck;
  readonly type: IdentifierType;
}): React.JSX.Element {
  if (!result.recognized) {
    return (
      <p className="flex items-start gap-2 rounded-xl bg-amber-50 px-3 py-2.5 text-sm text-amber-900 ring-1 ring-amber-200">
        <ShieldQuestion aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        {`Não reconhecemos este ${IDENTIFIER_TYPE_LABEL[type].toLowerCase()}. Confira o formato.`}
      </p>
    );
  }
  if (!result.blocked) {
    return (
      <p className="flex items-start gap-2 rounded-xl bg-emerald-50 px-3 py-2.5 text-sm text-emerald-900 ring-1 ring-emerald-200">
        <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        Nenhum pedido de não contato para este contato.
      </p>
    );
  }
  return (
    <p className="flex items-start gap-2 rounded-xl bg-red-50 px-3 py-2.5 text-sm font-medium text-red-900 ring-1 ring-red-200">
      <ShieldAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      {result.allChannels
        ? 'Bloqueado em todos os canais. Não aborde este contato.'
        : `Bloqueado em: ${result.channels.map((channel) => OUTREACH_CHANNEL_LABEL[channel]).join(', ')}.`}
    </p>
  );
}

function RegisterForm({ onRegistered }: { readonly onRegistered: () => void }): React.JSX.Element {
  const [identifierType, setIdentifierType] = useState<IdentifierType>('PHONE');
  const [identifier, setIdentifier] = useState('');
  const [channel, setChannel] = useState<OutreachChannel | undefined>(undefined);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<{ readonly ok: boolean; readonly text: string } | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  async function submit(): Promise<void> {
    setBusy(true);
    setFeedback(null);
    setFieldErrors(new Map());
    try {
      await registerSuppression({ identifierType, identifier, channel, reason });
      setIdentifier('');
      setReason('');
      setFeedback({ ok: true, text: 'Pedido registrado. Novas abordagens a este contato estão bloqueadas.' });
      onRegistered();
    } catch (cause: unknown) {
      const errors = fieldErrorsOf(cause);
      setFieldErrors(errors);
      // Identificador que não normaliza volta como 400 sem campo: o servidor não adivinha o formato.
      const unrecognized = cause instanceof HttpError && cause.type === 'VALIDATION_ERROR' && errors.size === 0;
      setFeedback({
        ok: false,
        text: unrecognized
          ? `Não foi possível reconhecer o ${IDENTIFIER_TYPE_LABEL[identifierType].toLowerCase()} informado.`
          : actionErrorMessage(cause),
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="registrar-bloqueio" className={`${card} p-4`}>
      <h2 id="registrar-bloqueio" className="text-sm font-semibold text-stone-900">
        Registrar pedido de não contato
      </h2>
      <p className="mt-1 text-xs text-stone-500">Vale na hora, para toda a equipe. Não há como remover depois.</p>
      <form
        className="mt-4 space-y-3"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <TypeChoice legend="Tipo de contato" value={identifierType} onChange={setIdentifierType} />
        <FormField
          label="Contato"
          value={identifier}
          onChange={setIdentifier}
          required
          maxLength={320}
          hint={IDENTIFIER_HINT[identifierType]}
          error={fieldErrors.get('identifier')}
        />
        <label className="block text-sm font-medium text-stone-700">
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
        <FormField
          label="Motivo"
          value={reason}
          onChange={setReason}
          required
          maxLength={500}
          error={fieldErrors.get('reason')}
        />
        <div role="group" aria-label="Sugestões de motivo" className="flex flex-wrap gap-1.5">
          {QUICK_REASONS.map((text) => (
            <button
              key={text}
              type="button"
              className="rounded-full bg-stone-100 px-2.5 py-1 text-xs font-medium text-stone-700 hover:bg-stone-200 focus-visible:outline-2 focus-visible:outline-brand-500"
              onClick={() => {
                setReason(text);
              }}
            >
              {text}
            </button>
          ))}
        </div>
        <button
          type="submit"
          className={`${buttonPrimary} w-full`}
          disabled={busy || identifier.trim() === '' || reason.trim() === ''}
        >
          {busy ? 'Registrando…' : 'Registrar pedido'}
        </button>
        <div aria-live="polite">
          {feedback !== null && (
            <p
              role={feedback.ok ? 'status' : 'alert'}
              className={`rounded-xl px-3 py-2 text-sm ${feedback.ok ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'}`}
            >
              {feedback.text}
            </p>
          )}
        </div>
      </form>
    </section>
  );
}

function SuppressionList({ refresh }: { readonly refresh: number }): React.JSX.Element {
  const [type, setType] = useState<IdentifierType | ''>('');
  const [draft, setDraft] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [state, reload] = useLoad(`${type}|${search}|${page}|${refresh}`, (signal) =>
    listSuppressions({ identifierType: type, search }, page, signal),
  );
  const shown = useLastReady(state);
  const [summary] = useLoad(`summary|${refresh}`, (signal) => getSuppressionSummary(signal));
  const counts = summary.kind === 'ready' ? summary.data : null;
  const filtered = type !== '' || search !== '';

  return (
    <section aria-labelledby="lista-bloqueios" className="space-y-4">
      <h2 id="lista-bloqueios" className="sr-only">
        Bloqueios registrados
      </h2>
      <dl className="grid grid-cols-3 gap-3">
        {IDENTIFIER_TYPES.map((item) => (
          <StatTile key={item} label={IDENTIFIER_TYPE_LABEL[item]} value={counts?.[item]} icon={TYPE_ICON[item]} />
        ))}
      </dl>

      <form
        role="search"
        aria-label="Buscar bloqueio"
        className={`${card} flex flex-wrap items-end gap-3 p-4`}
        onSubmit={(event) => {
          event.preventDefault();
          setSearch(draft.trim());
          setPage(0);
        }}
      >
        <label className="min-w-[12rem] flex-1 text-sm font-medium text-stone-700">
          Buscar contato
          <input
            type="search"
            className={input}
            placeholder="Telefone, e-mail ou @perfil, em qualquer formato"
            value={draft}
            onChange={(event) => {
              setDraft(event.target.value);
            }}
          />
        </label>
        <label className="text-sm font-medium text-stone-700">
          Tipo
          <select
            className={input}
            value={type}
            onChange={(event) => {
              setType(IDENTIFIER_TYPES.find((item) => item === event.target.value) ?? '');
              setPage(0);
            }}
          >
            <option value="">Todos</option>
            {IDENTIFIER_TYPES.map((item) => (
              <option key={item} value={item}>
                {IDENTIFIER_TYPE_LABEL[item]}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className={buttonPrimary}>
          Buscar
        </button>
      </form>

      {shown === null || (state.kind !== 'ready' && state.kind !== 'loading') ? (
        <LoadStateView state={state} onRetry={reload} />
      ) : shown.content.length === 0 ? (
        <EmptyState
          title={filtered ? 'Nenhum bloqueio para essa busca.' : 'Nenhum pedido de não contato registrado.'}
        />
      ) : (
        <div className={`transition-opacity ${state.kind === 'loading' ? 'opacity-60' : ''}`}>
          <Table entries={shown.content} />
          <Pagination page={shown} onChange={setPage} />
        </div>
      )}
    </section>
  );
}

function Table({ entries }: { readonly entries: readonly SuppressionEntry[] }): React.JSX.Element {
  return (
    <div className="relative overflow-x-auto rounded-2xl border border-stone-200 glass">
      <table className="min-w-full text-sm">
        <caption className="sr-only">Pedidos de não contato</caption>
        <thead className="border-b border-stone-200 bg-stone-50/70 text-left text-xs text-stone-500">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Contato
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Canal
            </th>
            <th scope="col" className="hidden px-4 py-2 font-medium md:table-cell">
              Motivo
            </th>
            <th scope="col" className="hidden px-4 py-2 font-medium sm:table-cell">
              Pedido em
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
          {entries.map((entry) => (
            <tr key={entry.id}>
              <td className="px-4 py-2">
                <p className="flex items-center gap-2 font-mono text-[13px] break-all text-stone-900">
                  <span aria-hidden="true" className="shrink-0 text-stone-400">
                    {TYPE_ICON[entry.identifierType]}
                  </span>
                  {readable(entry)}
                </p>
                <p className="pl-6 text-xs text-stone-500">{IDENTIFIER_TYPE_LABEL[entry.identifierType]}</p>
              </td>
              <td className="px-4 py-2">
                <span
                  className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold whitespace-nowrap ring-1 ${
                    entry.channel === undefined
                      ? 'bg-red-50 text-red-800 ring-red-200'
                      : 'bg-stone-100 text-stone-700 ring-stone-200'
                  }`}
                >
                  {entry.channel === undefined ? 'Todos' : OUTREACH_CHANNEL_LABEL[entry.channel]}
                </span>
              </td>
              <td className="hidden max-w-[22rem] px-4 py-2 text-stone-600 md:table-cell">
                <span className="line-clamp-2">{entry.reason}</span>
              </td>
              <td className="hidden px-4 py-2 whitespace-nowrap text-stone-600 sm:table-cell">
                {formatDateTime(entry.requestedAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
