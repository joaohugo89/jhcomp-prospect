import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { useLoad } from '../../app/useLoad';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { DataTable, type Column } from '../../components/DataTable';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { LoadStateView } from '../../components/States';
import { input, link } from '../../components/styles';
import { fetchMembers, resetMemberPassword, updateMember } from '../../services/memberService';
import type { Member, MemberWithPassword } from '../../types/member';
import type { OrganizationRole, Session } from '../../types/session';
import { NewMemberForm } from './NewMemberForm';
import { TemporaryPasswordNotice } from './TemporaryPasswordNotice';

const ROLE_LABEL: Readonly<Record<OrganizationRole, string>> = { ADMIN: 'Administrador', OPERATOR: 'Operador' };

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
  const [state, reload] = useLoad(`${page}:${version}`, (signal) => fetchMembers(page, signal));
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

  const columns: readonly Column<Member>[] = [
    { header: 'Nome', render: (member) => member.displayName },
    { header: 'E-mail', render: (member) => <span className="break-all">{member.email}</span> },
    {
      header: 'Papel',
      render: (member) =>
        member.userId === session.userId || member.status === 'REVOKED' ? (
          ROLE_LABEL[member.role]
        ) : (
          <select
            aria-label={`Papel de ${member.displayName}`}
            className={`${input} mt-0 w-auto`}
            value={member.role}
            disabled={busy}
            onChange={(event) => {
              const role = event.target.value === 'ADMIN' ? 'ADMIN' : 'OPERATOR';
              if (role !== member.role) {
                setPending({ kind: 'role', member, role });
              }
            }}
          >
            <option value="OPERATOR">{ROLE_LABEL.OPERATOR}</option>
            <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
          </select>
        ),
    },
    { header: 'Acesso', render: (member) => (member.status === 'ACTIVE' ? 'Ativo' : 'Removido') },
    { header: 'Desde', render: (member) => formatDateTime(member.createdAt), hideOnSmall: true },
    {
      header: 'Ações',
      render: (member) =>
        member.userId === session.userId ? (
          <span className="text-xs text-slate-500">Você</span>
        ) : (
          <span className="flex flex-wrap gap-2">
            {member.status === 'ACTIVE' ? (
              <>
                <button
                  type="button"
                  className={`${link} text-sm`}
                  disabled={busy}
                  onClick={() => {
                    setPending({ kind: 'reset', member });
                  }}
                >
                  Redefinir senha
                </button>
                <button
                  type="button"
                  className={`${link} text-sm text-red-700`}
                  disabled={busy}
                  onClick={() => {
                    setPending({ kind: 'revoke', member });
                  }}
                >
                  Remover acesso
                </button>
              </>
            ) : (
              <button
                type="button"
                className={`${link} text-sm`}
                disabled={busy}
                onClick={() => {
                  setPending({ kind: 'reactivate', member });
                }}
              >
                Reativar acesso
              </button>
            )}
          </span>
        ),
    },
  ];

  const dialog = pending === null ? null : CONFIRMATION[pending.kind](pending.member);

  return (
    <>
      <PageHeader title="Membros" description="Pessoas com acesso a esta organização." />
      <div className="space-y-6">
        <NewMemberForm
          onCreated={(created) => {
            setIssued(created);
            refresh();
          }}
        />

        {issued !== null && (
          <TemporaryPasswordNotice
            issued={issued}
            onDismiss={() => {
              setIssued(null);
            }}
          />
        )}

        {feedback !== null && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
            {feedback}
          </p>
        )}

        {state.kind === 'ready' ? (
          <>
            <DataTable caption="Membros" columns={columns} rows={state.data.content} rowKey={(row) => row.memberId} />
            <Pagination page={state.data} onChange={setPage} />
          </>
        ) : (
          <LoadStateView state={state} onRetry={reload} />
        )}
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
