import type { Session } from '../../types/session';
import { LoginForm } from './LoginForm';

interface LoginPageProps {
  readonly sessionExpired: boolean;
  readonly onAuthenticated: (session: Session) => void;
}

/** Entrada na aplicação (RF-001). */
export function LoginPage({ sessionExpired, onAuthenticated }: LoginPageProps): React.JSX.Element {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-slate-50 px-4 py-10 text-slate-900">
      <div className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-semibold tracking-tight">JH.COMP Prospect</h1>
        <p className="mt-1 text-center text-sm text-slate-600">Acesso restrito à equipe.</p>

        <div className="mt-8 rounded-2xl border border-slate-200/70 bg-white p-6 shadow-sm">
          {sessionExpired && (
            <p role="status" className="mb-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900">
              Sua sessão expirou ou foi encerrada. Entre novamente para continuar.
            </p>
          )}
          <h2 className="mb-4 text-lg font-medium">Entrar</h2>
          <LoginForm onAuthenticated={onAuthenticated} />
        </div>
      </div>
    </main>
  );
}
