import type { Session } from '../../types/session';
import { LoginForm } from './LoginForm';

interface LoginPageProps {
  readonly sessionExpired: boolean;
  readonly onAuthenticated: (session: Session) => void;
}

/** The way into the application (RF-001). */
export function LoginPage({ sessionExpired, onAuthenticated }: LoginPageProps): React.JSX.Element {
  return (
    // The same entrance as the JH Comp site: oxblood background, serif title, red italic accent.
    <main
      data-theme="dark"
      className="brand-backdrop flex min-h-dvh items-center justify-center px-4 py-10 text-stone-900"
    >
      <div className="w-full max-w-sm">
        <p className="text-center font-mono text-[11px] font-semibold tracking-[0.2em] text-cream/60 uppercase">
          JH Comp · Prospecção
        </p>
        <h1 className="mt-2 text-center font-display text-4xl font-semibold tracking-tight text-cream">
          JH.COMP <em className="font-normal text-brand-200">Prospect</em>
        </h1>
        <p className="mt-2 text-center text-sm text-cream/70">Acesso restrito à equipe.</p>

        <div className="mt-8 rounded-2xl border border-white/10 glass p-6 shadow-2xl shadow-black/30">
          {sessionExpired && (
            <p role="status" className="mb-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-900">
              Sua sessão expirou ou foi encerrada. Entre novamente para continuar.
            </p>
          )}
          <h2 className="mb-4 font-display text-xl font-semibold">Entrar</h2>
          <LoginForm onAuthenticated={onAuthenticated} />
        </div>
      </div>
    </main>
  );
}
