import { useState } from 'react';

import { PageHeader } from '../../components/PageHeader';
import { buttonSecondary } from '../../components/styles';
import type { Session } from '../../types/session';
import { ChangePasswordForm } from './ChangePasswordForm';

/** Troca voluntária, dentro da aplicação (/settings/password). */
export function PasswordPage({ onChanged }: { readonly onChanged: (session: Session) => void }): React.JSX.Element {
  const [done, setDone] = useState(false);
  return (
    <>
      <PageHeader title="Minha senha" description="Troque a senha usada para entrar no JH.COMP Prospect." />
      <section className="max-w-md rounded-2xl border border-stone-200/70 glass p-6 shadow-sm">
        {done ? (
          <p role="status" className="text-sm text-emerald-800">
            Senha trocada. As sessões abertas em outros navegadores foram encerradas.
          </p>
        ) : (
          <ChangePasswordForm
            onChanged={(session) => {
              setDone(true);
              onChanged(session);
            }}
          />
        )}
      </section>
    </>
  );
}

/**
 * Primeiro acesso com senha temporária (D28). Fora da estrutura da aplicação:
 * o servidor recusa qualquer outra tela até a troca, então mostrar o menu só
 * levaria a erros.
 */
export function ForcedPasswordChange({
  session,
  onChanged,
  onSignOut,
}: {
  readonly session: Session;
  readonly onChanged: (session: Session) => void;
  readonly onSignOut: () => void;
}): React.JSX.Element {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-stone-50 px-4 py-10">
      <section
        aria-labelledby="troca-obrigatoria"
        className="w-full max-w-md rounded-2xl border border-stone-200/70 glass p-6 shadow-sm"
      >
        <h1 id="troca-obrigatoria" className="font-display text-2xl font-semibold text-stone-900">
          Defina sua senha
        </h1>
        <p className="mt-2 text-sm text-stone-700">
          {`Olá, ${session.displayName}. Você entrou com uma senha temporária, criada pelo administrador. Defina uma senha sua para continuar.`}
        </p>
        <div className="mt-4">
          <ChangePasswordForm onChanged={onChanged} />
        </div>
        <button type="button" className={`${buttonSecondary} mt-4`} onClick={onSignOut}>
          Sair
        </button>
      </section>
    </main>
  );
}
