import { useEffect, useState } from 'react';

import { logout } from '../../services/authService';
import { HttpError } from '../../services/httpClient';
import { fetchMembers } from '../../services/memberService';
import type { Member } from '../../types/member';
import type { Session } from '../../types/session';

interface SessionPanelProps {
  readonly session: Session;
  readonly onSignedOut: () => void;
}

type MembersState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'ready'; readonly members: readonly Member[] }
  | { readonly kind: 'forbidden' }
  | { readonly kind: 'error'; readonly message: string };

/**
 * Painel da sessão autenticada.
 *
 * Provisório, como o LoginForm: serve para conferir manualmente o contexto de
 * organização e a restrição por papel entregues na Etapa 3.
 */
export function SessionPanel({ session, onSignedOut }: SessionPanelProps): React.JSX.Element {
  const [members, setMembers] = useState<MembersState>({ kind: 'loading' });
  const [signingOut, setSigningOut] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    fetchMembers(controller.signal)
      .then((page) => {
        setMembers({ kind: 'ready', members: page.content });
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        if (cause instanceof HttpError && cause.status === 403) {
          // Esperado para operador: a listagem é operação administrativa (RF-003).
          setMembers({ kind: 'forbidden' });
          return;
        }
        setMembers({ kind: 'error', message: 'Não foi possível carregar os membros.' });
      });

    return () => {
      controller.abort();
    };
  }, []);

  async function handleLogout(): Promise<void> {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      // Mesmo que a chamada falhe, a interface volta ao login: insistir em
      // mostrar uma sessão que talvez não exista mais seria pior.
      setSigningOut(false);
      onSignedOut();
    }
  }

  return (
    <div className="space-y-6">
      <section aria-labelledby="session-heading">
        <h2 id="session-heading" className="text-sm font-medium uppercase text-slate-500">
          Sessão
        </h2>
        <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-slate-500">Usuário</dt>
          <dd className="font-medium">{session.displayName}</dd>
          <dt className="text-slate-500">E-mail</dt>
          <dd className="font-mono text-xs break-all">{session.email}</dd>
          <dt className="text-slate-500">Papel</dt>
          <dd className="font-medium">{session.role}</dd>
          <dt className="text-slate-500">Organização</dt>
          <dd className="font-mono text-xs break-all">{session.organizationId}</dd>
        </dl>
      </section>

      <section aria-labelledby="members-heading">
        <h2 id="members-heading" className="text-sm font-medium uppercase text-slate-500">
          Membros da organização
        </h2>

        <div aria-live="polite" className="mt-2 text-sm">
          {members.kind === 'loading' && <p className="text-slate-600">Carregando…</p>}

          {members.kind === 'forbidden' && (
            <p className="rounded-md bg-amber-50 px-3 py-2 text-amber-800">
              Seu papel ({session.role}) não permite listar membros. Isso é o comportamento
              esperado: a listagem é restrita a administradores.
            </p>
          )}

          {members.kind === 'error' && (
            <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-red-800">
              {members.message}
            </p>
          )}

          {members.kind === 'ready' && members.members.length === 0 && (
            <p className="text-slate-600">Nenhum membro ativo.</p>
          )}

          {members.kind === 'ready' && members.members.length > 0 && (
            <ul className="mt-1 divide-y divide-slate-200 rounded-md border border-slate-200">
              {members.members.map((member) => (
                <li key={member.memberId} className="flex items-baseline justify-between px-3 py-2">
                  <span>
                    <span className="font-medium">{member.displayName}</span>{' '}
                    <span className="font-mono text-xs text-slate-500">{member.email}</span>
                  </span>
                  <span className="text-xs font-medium text-slate-600">{member.role}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <button
        type="button"
        onClick={() => void handleLogout()}
        disabled={signingOut}
        className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500 disabled:opacity-60"
      >
        {signingOut ? 'Saindo…' : 'Sair'}
      </button>
    </div>
  );
}
