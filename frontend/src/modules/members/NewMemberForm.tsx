import { useState } from 'react';

import { actionErrorMessage, fieldErrorsOf } from '../../app/errors';
import { FormField } from '../../components/FormField';
import { buttonPrimary, input } from '../../components/styles';
import { createMember } from '../../services/memberService';
import type { MemberWithPassword } from '../../types/member';
import type { OrganizationRole } from '../../types/session';

/**
 * Cadastro de membro (D28). Não há envio de convite: o sistema gera uma senha
 * temporária, que o administrador entrega por um canal seguro.
 */
export function NewMemberForm({
  onCreated,
}: {
  readonly onCreated: (created: MemberWithPassword) => void;
}): React.JSX.Element {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [role, setRole] = useState<OrganizationRole>('OPERATOR');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  async function submit(): Promise<void> {
    setBusy(true);
    setError(null);
    setFieldErrors(new Map());
    try {
      onCreated(await createMember(email, displayName, role));
      setEmail('');
      setDisplayName('');
      setRole('OPERATOR');
    } catch (cause: unknown) {
      setFieldErrors(fieldErrorsOf(cause));
      setError(actionErrorMessage(cause, 'Este e-mail já está cadastrado.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="novo-membro" className="rounded-lg border border-slate-200 bg-white p-6">
      <h2 id="novo-membro" className="text-base font-semibold">
        Adicionar membro
      </h2>
      <form
        className="mt-3 grid gap-3 sm:grid-cols-3"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <FormField
          label="Nome"
          value={displayName}
          onChange={setDisplayName}
          required
          maxLength={120}
          error={fieldErrors.get('displayName')}
        />
        <FormField
          label="E-mail"
          type="email"
          value={email}
          onChange={setEmail}
          required
          maxLength={254}
          autoComplete="off"
          error={fieldErrors.get('email')}
        />
        <label className="text-sm font-medium text-slate-700">
          Papel
          <select
            className={input}
            value={role}
            onChange={(event) => {
              setRole(event.target.value === 'ADMIN' ? 'ADMIN' : 'OPERATOR');
            }}
          >
            <option value="OPERATOR">Operador</option>
            <option value="ADMIN">Administrador</option>
          </select>
        </label>
        <div className="sm:col-span-3">
          <button
            type="submit"
            className={buttonPrimary}
            disabled={busy || email.trim() === '' || displayName.trim() === ''}
          >
            {busy ? 'Adicionando…' : 'Adicionar membro'}
          </button>
          {error !== null && (
            <p role="alert" className="mt-2 text-sm text-red-700">
              {error}
            </p>
          )}
        </div>
      </form>
    </section>
  );
}
