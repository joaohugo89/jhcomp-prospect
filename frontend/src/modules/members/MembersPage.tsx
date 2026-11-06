import { KeyRound, ShieldCheck, UserCheck, UserX } from 'lucide-react';
import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime, initials } from '../../app/format';
import { useLastReady, useLoad } from '../../app/useLoad';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { PageHeader } from '../../components/PageHeader';
import { StatTile } from '../../components/StatTile';
import { Pagination } from '../../components/Pagination';
import { EmptyState, LoadStateView } from '../../components/States';
import { buttonPrimary, card, chip, input } from '../../components/styles';
import { fetchMembers, getTeamSummary, resetMemberPassword, updateMember } from '../../services/memberService';
import type { Member, MemberStatus, MemberWithPassword } from '../../types/member';
import type { OrganizationRole, Session } from '../../types/session';
import { NewMemberForm } from './NewMemberForm';
import { TemporaryPasswordNotice } from './TemporaryPasswordNotice';

const ROLE_LABEL: Readonly<Record<OrganizationRole, string>> = { ADMIN: 'Administrador', OPERATOR: 'Operador' };

const DAY = 86_400_000;

type PendingAction =
  | { readonly kind: 'revoke' | 'reactivate' | 'reset'; readonly member: Member }
  | { readonly kind: 'role'; readonly member: Member; readonly role: OrganizationRole };

const CONFIRMATION: Readonly<
  Record<PendingAction['kind'], (member: Member) => { title: string; description: string; label: string }>
> = {
  revoke: (member) => ({
    title: `Remover o acesso de ${member.displayName}?`,
    description: 'A sessão aberta é encerrada na hora. Os registros feitos por esta pessoa continuam no sistema.',
    label: 'Remover acesso',
  }),
  reactivate: (member) => ({
    title: `Reativar o acesso de ${member.displayName}?`,
    description: 'A pessoa volta a entrar com a senha que já tinha.',
    label: 'Reativar acesso',
  }),
  reset: (member) => ({
    title: `Redefinir a senha de ${member.displayName}?`,
    description:
      'Uma senha temporária nova é gerada, as sessões abertas são encerradas e a pessoa precisará trocá-la no próximo acesso.',
    label: 'Redefinir senha',
  }),
  role: (member) => ({
    title: `Mudar o papel de ${member.displayName}?`,
    description: 'Vale na próxima ação da pessoa, sem precisar entrar de novo.',
    label: 'Mudar papel',
  }),
};

const FILTERS: readonly [MemberStatus | '', string][] = [
  ['', 'Todos'],
  ['ACTIVE', 'Ativos'],
  ['REVOKED', 'Removidos'],
];

/** "hoje", "ontem", "há 5 dias", ou a data quando passa de um mês. */
function lastAccess(member: Member, now: number): string {
  if (member.lastLoginAt === undefined) {
    return 'Nunca entrou';
  }
  const days = Math.floor((now - Date.parse(member.lastLoginAt)) / DAY);
  if (days <= 0) return 'Hoje';
  if (days === 1) return 'Ontem';
  if (days < 30) return `Há ${days} dias`;
  return formatDateTime(member.lastLoginAt);
}

/**
 * Membros da organização (RF-003, D28). Só administradores chegam aqui pelo
 * menu; quem digitar o endereço sem permissão vê o estado de acesso negado.
 *
 * A própria linha do administrador não tem ações: o servidor recusa que
 * alguém mude o próprio papel ou acesso, o que impede ficar sem administrador.
 */
export function MembersPage({ session }: { readonly session: Session }): React.JSX.Element {
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [status, setStatus] = useState<MemberStatus | ''>('');
  const [draft, setDraft] = useState('');
  const [search, setSearch] = useState('');
  const [state, reload] = useLoad(`${status}|${search}|${page}|${version}`, async (signal) => ({
    page: await fetchMembers({ status, search }, page, signal),
    receivedAt: Date.now(),
  }));
  const shown = useLastReady(state);
  const [summary] = useLoad(`summary|${version}`, (signal) => getTeamSummary(signal));
  const [pending, setPending] = useState<PendingAction | null>(null);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [issued, setIssued] = useState<MemberWithPassword | null>(null);

  function refresh(): void {
    setVersion((value) => value + 1);
  }

  async function confirm(action: PendingAction): Promise<void> {
    setBusy(true);
    setFeedback(null);
    try {
      switch (action.kind) {
        case 'revoke':
          await updateMember(action.member.memberId, { status: 'REVOKED' });
          break;
        case 'reactivate':
          await updateMember(action.member.memberId, { status: 'ACTIVE' });
          break;
        case 'role':
          await updateMember(action.member.memberId, { role: action.role });
          break;
        case 'reset':
          setIssued(await resetMemberPassword(action.member.memberId));
          break;
      }
      refresh();
    } catch (cause: unknown) {
      setFeedback(actionErrorMessage(cause, 'A alteração não é permitida para este membro.'));
    } finally {
      setBusy(false);
      setPending(null);
    }
  }

  const dialog = pending === null ? null : CONFIRMATION[pending.kind](pending.member);
  const counts = summary.kind === 'ready' ? summary.data : null;
  const filtered = status !== '' || search !== '';

  return (
    <>
      <PageHeader title="Membros" description="Pessoas com acesso a esta organização e o que cada uma pode fazer." />

      <dl className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        {(
          [
            ['Ativos', counts?.active, 'text-stone-900'],
            ['Administradores', counts?.admins, 'text-stone-900'],
            [
              'Aguardando 1º acesso',
              counts?.awaitingFirstAccess,
              (counts?.awaitingFirstAccess ?? 0) > 0 ? 'text-amber-700' : 'text-stone-900',
            ],
            ['Removidos', counts?.revoked, 'text-stone-900'],
          ] as const
        ).map(([label, value, tone]) => (
          <StatTile key={label} label={label} value={value} tone={tone} />
        ))}
      </dl>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-4">
          {issued !== null && (
            <TemporaryPasswordNotice
              issued={issued}
              onDismiss={() => {
                setIssued(null);
              }}
            />
          )}
          {feedback !== null && (
            <p role="alert" className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-800">
              {feedback}
            </p>
          )}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div role="group" aria-label="Filtrar por situação" className="flex flex-wrap gap-1.5">
              {FILTERS.map(([value, label]) => (
                <button
                  key={label}
                  type="button"
                  aria-pressed={status === value}
                  className={chip(status === value)}
                  onClick={() => {
                    setStatus(value);
                    setPage(0);
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
            <form
              role="search"
              aria-label="Buscar membro"
              className="flex gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                setSearch(draft.trim());
                setPage(0);
              }}
            >
              <label>
                <span className="sr-only">Buscar por nome ou e-mail</span>
                <input
                  type="search"
                  placeholder="Nome ou e-mail"
                  className={`${input} mt-0 h-9 w-56`}
                  value={draft}
                  onChange={(event) => {
                    setDraft(event.target.value);
                  }}
                />
              </label>
              <button type="submit" className={`${buttonPrimary} h-9 py-0`}>
                Buscar
              </button>
            </form>
          </div>

          {shown === null || (state.kind !== 'ready' && state.kind !== 'loading') ? (
            <LoadStateView state={state} onRetry={reload} />
          ) : shown.page.content.length === 0 ? (
            <EmptyState title={filtered ? 'Nenhum membro corresponde à busca.' : 'Nenhum membro ainda.'} />
          ) : (
            <div className={`transition-opacity ${state.kind === 'loading' ? 'opacity-60' : ''}`}>
              <MemberTable
                members={shown.page.content}
                now={shown.receivedAt}
                session={session}
                busy={busy}
                onAction={setPending}
              />
              <Pagination page={shown.page} onChange={setPage} />
            </div>
          )}
        </div>

        <div className="space-y-4">
          <NewMemberForm
            onCreated={(created) => {
              setIssued(created);
              refresh();
            }}
          />
          <section aria-labelledby="papeis" className={`${card} p-4 text-sm`}>
            <h2 id="papeis" className="flex items-center gap-2 font-semibold text-stone-900">
              <ShieldCheck aria-hidden="true" className="size-4 text-emerald-600" />
              Papéis e segurança
            </h2>
            <ul className="mt-3 space-y-2 text-stone-600">
              <li>
                <strong className="font-semibold text-stone-800">Operador</strong> conduz o trabalho comercial.
              </li>
              <li>
                <strong className="font-semibold text-stone-800">Administrador</strong> também gerencia membros, vê a
                lista de não contato e corrige cadastros feitos por engano.
              </li>
              <li>Remover acesso ou redefinir a senha encerra as sessões abertas da pessoa na hora.</li>
              <li>Ninguém altera o próprio papel ou acesso: a organização nunca fica sem administrador.</li>
            </ul>
          </section>
        </div>
      </div>

      {pending !== null && dialog !== null && (
        <ConfirmDialog
          title={dialog.title}
          description={dialog.description}
          confirmLabel={dialog.label}
          destructive={pending.kind === 'revoke' || pending.kind === 'reset'}
          busy={busy}
          onConfirm={() => {
            void confirm(pending);
          }}
          onCancel={() => {
            setPending(null);
          }}
        />
      )}
    </>
  );
}

function MemberTable({
  members,
  now,
  session,
  busy,
  onAction,
}: {
  readonly members: readonly Member[];
  readonly now: number;
  readonly session: Session;
  readonly busy: boolean;
  readonly onAction: (action: PendingAction) => void;
}): React.JSX.Element {
  const action =
    'inline-flex items-center gap-1 whitespace-nowrap rounded-lg px-2 py-1 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-brand-500 disabled:opacity-50';
  return (
    // No celular cada pessoa vira um cartão (linhas em bloco), sem rolagem lateral escondendo papel e ações.
    <div className="relative overflow-hidden rounded-2xl border border-stone-200 bg-white sm:overflow-x-auto">
      <table className="block w-full text-sm sm:table">
        <caption className="sr-only">Membros</caption>
        <thead className="hidden border-b border-stone-200 bg-stone-50/70 text-left text-xs text-stone-500 sm:table-header-group">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Pessoa
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Papel
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Último acesso
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              <span className="sr-only">Ações</span>
            </th>
          </tr>
        </thead>
        <tbody className="block divide-y divide-stone-100 sm:table-row-group">
          {members.map((member) => {
            const self = member.userId === session.userId;
            const revoked = member.status === 'REVOKED';
            return (
              <tr
                key={member.memberId}
                className={`block py-2 sm:table-row sm:py-0 ${revoked ? 'bg-stone-50/60' : ''}`}
              >
                <td className="block px-4 py-2 sm:table-cell sm:max-w-[18rem] sm:py-2">
                  <p className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 font-semibold text-stone-900">
                    <span
                      aria-hidden="true"
                      className={`mr-1.5 inline-flex size-9 shrink-0 items-center justify-center rounded-full text-xs font-bold ${
                        revoked ? 'bg-stone-200 text-stone-500' : 'bg-brand-50 text-brand-700 ring-1 ring-brand-100'
                      }`}
                    >
                      {initials(member.displayName)}
                    </span>
                    <span className={revoked ? 'text-stone-500 line-through' : ''}>{member.displayName}</span>
                    {self && (
                      <span className="rounded-full bg-stone-900 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                        Você
                      </span>
                    )}
                    {revoked && (
                      <span className="inline-flex items-center gap-0.5 rounded-full bg-stone-200 px-1.5 py-0.5 text-[10px] font-semibold text-stone-700">
                        <UserX aria-hidden="true" className="size-3" />
                        Acesso removido
                      </span>
                    )}
                    {!revoked && member.passwordChangeRequired && (
                      <span className="inline-flex items-center gap-0.5 rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] font-semibold text-amber-800 ring-1 ring-amber-200">
                        <KeyRound aria-hidden="true" className="size-3" />
                        Senha temporária
                      </span>
                    )}
                  </p>
                  <p className="-mt-2 truncate pl-12 text-xs text-stone-500">{member.email}</p>
                </td>
                <td className="inline-block px-4 py-1 align-middle sm:table-cell sm:py-2">
                  {self || revoked ? (
                    <span className="text-stone-700">{ROLE_LABEL[member.role]}</span>
                  ) : (
                    <select
                      aria-label={`Papel de ${member.displayName}`}
                      className={`${input} mt-0 h-9 w-40 min-w-36 py-0`}
                      value={member.role}
                      disabled={busy}
                      onChange={(event) => {
                        const role = event.target.value === 'ADMIN' ? 'ADMIN' : 'OPERATOR';
                        if (role !== member.role) {
                          onAction({ kind: 'role', member, role });
                        }
                      }}
                    >
                      <option value="OPERATOR">{ROLE_LABEL.OPERATOR}</option>
                      <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
                    </select>
                  )}
                </td>
                <td className="inline-block px-4 py-1 align-middle whitespace-nowrap sm:table-cell sm:py-2">
                  <span className={member.lastLoginAt === undefined ? 'text-stone-400' : 'text-stone-700'}>
                    {lastAccess(member, now)}
                  </span>
                  <span className="block text-xs text-stone-400">{`Desde ${formatDateTime(member.createdAt)}`}</span>
                </td>
                <td className="block px-3 py-1 sm:table-cell sm:py-2">
                  {self ? null : (
                    <div className="flex flex-wrap gap-1 sm:justify-end">
                      {revoked ? (
                        <button
                          aria-label="Reativar acesso"
                          title="Reativar acesso"
                          type="button"
                          className={`${action} text-brand-700 hover:bg-brand-50`}
                          disabled={busy}
                          onClick={() => {
                            onAction({ kind: 'reactivate', member });
                          }}
                        >
                          <UserCheck aria-hidden="true" className="size-3.5" />
                          <span className="sm:hidden 2xl:inline">Reativar acesso</span>
                        </button>
                      ) : (
                        <>
                          <button
                            aria-label="Redefinir senha"
                            title="Redefinir senha"
                            type="button"
                            className={`${action} text-stone-700 hover:bg-stone-100`}
                            disabled={busy}
                            onClick={() => {
                              onAction({ kind: 'reset', member });
                            }}
                          >
                            <KeyRound aria-hidden="true" className="size-3.5" />
                            <span className="sm:hidden 2xl:inline">Redefinir senha</span>
                          </button>
                          <button
                            aria-label="Remover acesso"
                            title="Remover acesso"
                            type="button"
                            className={`${action} text-red-700 hover:bg-red-50`}
                            disabled={busy}
                            onClick={() => {
                              onAction({ kind: 'revoke', member });
                            }}
                          >
                            <UserX aria-hidden="true" className="size-3.5" />
                            <span className="sm:hidden 2xl:inline">Remover acesso</span>
                          </button>
                        </>
                      )}
                    </div>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
