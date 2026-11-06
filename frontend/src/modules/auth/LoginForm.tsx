import { useState } from 'react';

import { login } from '../../services/authService';
import { HttpError } from '../../services/httpClient';
import type { Session } from '../../types/session';

interface LoginFormProps {
  readonly onAuthenticated: (session: Session) => void;
}

/**
 * Formulário de login (RF-001).
 *
 * A mensagem de falha é a mesma para qualquer credencial inválida: distinguir
 * "e-mail inexistente" de "senha errada" permitiria enumerar contas.
 */
export function LoginForm({ onAuthenticated }: LoginFormProps): React.JSX.Element {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<ReadonlyMap<string, string>>(new Map());

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setSubmitting(true);
    setFormError(null);
    setFieldErrors(new Map());

    try {
      onAuthenticated(await login(email, password));
    } catch (cause: unknown) {
      if (cause instanceof HttpError) {
        setFieldErrors(cause.fieldErrors);
        // A API devolve a mesma mensagem para qualquer falha de credencial, de
        // propósito: distinguir os casos permitiria enumerar contas (RF-001).
        setFormError(
          cause.status === 429
            ? 'Muitas tentativas. Aguarde alguns minutos e tente novamente.'
            : cause.fieldErrors.size > 0
              ? 'Corrija os campos destacados.'
              : 'E-mail ou senha inválidos.',
        );
      } else {
        setFormError('Não foi possível falar com a API. Verifique se o backend está no ar.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  const emailError = fieldErrors.get('email');
  const passwordError = fieldErrors.get('password');

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      <div>
        <label htmlFor="email" className="block text-sm font-medium text-stone-700">
          E-mail
        </label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="username"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={emailError !== undefined}
          aria-describedby={emailError === undefined ? undefined : 'email-error'}
          className="mt-1 w-full rounded-md border border-stone-300 px-3 py-2 text-stone-900 focus:border-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
        />
        {emailError !== undefined && (
          <p id="email-error" className="mt-1 text-sm text-red-700">
            {emailError}
          </p>
        )}
      </div>

      <div>
        <label htmlFor="password" className="block text-sm font-medium text-stone-700">
          Senha
        </label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={passwordError !== undefined}
          aria-describedby={passwordError === undefined ? undefined : 'password-error'}
          className="mt-1 w-full rounded-md border border-stone-300 px-3 py-2 text-stone-900 focus:border-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
        />
        {passwordError !== undefined && (
          <p id="password-error" className="mt-1 text-sm text-red-700">
            {passwordError}
          </p>
        )}
      </div>

      {/* aria-live para que o leitor de tela anuncie a falha sem mover o foco. */}
      <div aria-live="polite">
        {formError !== null && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
            {formError}
          </p>
        )}
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-md bg-brand-600 px-4 py-2 font-medium text-white hover:bg-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500 disabled:cursor-not-allowed disabled:opacity-60"
      >
        {submitting ? 'Entrando…' : 'Entrar'}
      </button>
    </form>
  );
}
