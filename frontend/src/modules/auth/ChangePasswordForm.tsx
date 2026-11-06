import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { FormField } from '../../components/FormField';
import { buttonPrimary } from '../../components/styles';
import { changePassword } from '../../services/authService';
import { HttpError } from '../../services/httpClient';
import type { Session } from '../../types/session';

/** Mesmo mínimo do servidor (PasswordPolicy, D28): comprimento, não regra de composição. */
const MIN_LENGTH = 12;

function failureMessage(cause: unknown): string {
  if (cause instanceof HttpError && cause.type === 'INVALID_CREDENTIALS') {
    return 'A senha atual não confere.';
  }
  if (cause instanceof HttpError && cause.type === 'VALIDATION_ERROR') {
    return `A nova senha precisa ter ao menos ${MIN_LENGTH} caracteres e ser diferente da atual.`;
  }
  return actionErrorMessage(cause);
}

/**
 * Troca da própria senha (D28). Pede a senha atual também quando ela é
 * temporária: quem só tem a sessão aberta não consegue tomar a conta.
 */
export function ChangePasswordForm({
  onChanged,
}: {
  readonly onChanged: (session: Session) => void;
}): React.JSX.Element {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tooShort = next.length > 0 && [...next].length < MIN_LENGTH;
  const mismatch = confirmation.length > 0 && confirmation !== next;
  const ready = current !== '' && [...next].length >= MIN_LENGTH && next === confirmation;

  async function submit(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      onChanged(await changePassword(current, next));
    } catch (cause: unknown) {
      setError(failureMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      aria-label="Trocar senha"
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <FormField
        label="Senha atual"
        type="password"
        value={current}
        onChange={setCurrent}
        required
        maxLength={128}
        autoComplete="current-password"
      />
      <FormField
        label="Nova senha"
        type="password"
        value={next}
        onChange={setNext}
        required
        maxLength={72}
        autoComplete="new-password"
        hint={`Ao menos ${MIN_LENGTH} caracteres. Uma frase longa é mais segura e fácil de lembrar que símbolos.`}
        error={tooShort ? `Use ao menos ${MIN_LENGTH} caracteres.` : undefined}
      />
      <FormField
        label="Repita a nova senha"
        type="password"
        value={confirmation}
        onChange={setConfirmation}
        required
        maxLength={72}
        autoComplete="new-password"
        error={mismatch ? 'As senhas não conferem.' : undefined}
      />
      <p className="text-xs text-stone-500">Trocar a senha encerra suas sessões abertas em outros navegadores.</p>
      {error !== null && (
        <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
        </p>
      )}
      <button type="submit" className={buttonPrimary} disabled={busy || !ready}>
        {busy ? 'Salvando…' : 'Trocar senha'}
      </button>
    </form>
  );
}
