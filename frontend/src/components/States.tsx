import type { Load } from '../app/useLoad';
import { buttonSecondary } from './styles';

/** The interface states from doc 08 §11. Useful messages, free of internal detail. */

export function LoadingState({ label = 'Carregando…' }: { readonly label?: string }): React.JSX.Element {
  return (
    <p role="status" className="py-8 text-center text-sm text-stone-600">
      {label}
    </p>
  );
}

interface EmptyStateProps {
  readonly title: string;
  readonly description?: string;
  readonly action?: React.ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps): React.JSX.Element {
  return (
    <div className="rounded-lg border border-dashed border-stone-300 glass px-6 py-10 text-center">
      <p className="font-medium text-stone-800">{title}</p>
      {description !== undefined && <p className="mt-1 text-sm text-stone-600">{description}</p>}
      {action !== undefined && <div className="mt-4">{action}</div>}
    </div>
  );
}

interface ErrorStateProps {
  readonly message?: string;
  readonly onRetry?: () => void;
}

function ErrorState({
  message = 'Não foi possível carregar os dados. Tente novamente em instantes.',
  onRetry,
}: ErrorStateProps): React.JSX.Element {
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-6 py-6 text-center text-sm text-red-900">
      <p>{message}</p>
      {onRetry !== undefined && (
        <button type="button" onClick={onRetry} className={`${buttonSecondary} mt-4`}>
          Tentar novamente
        </button>
      )}
    </div>
  );
}

/**
 * Renders every state that is not "ready", or nothing at all.
 *
 * It centralises the decision so no screen forgets a case: the caller only has
 * to handle the loaded data.
 */
export function LoadStateView({
  state,
  onRetry,
  notFoundMessage = 'Registro não encontrado.',
}: {
  readonly state: Load<unknown>;
  readonly onRetry: () => void;
  readonly notFoundMessage?: string;
}): React.JSX.Element | null {
  switch (state.kind) {
    case 'loading':
      return <LoadingState />;
    case 'notFound':
      // A resource from another organisation also lands here: the API answers
      // 404 on purpose, and the screen never suggests that it exists.
      return <EmptyState title={notFoundMessage} />;
    case 'forbidden':
      return (
        <EmptyState title="Sem permissão" description="Seu papel nesta organização não permite acessar esta área." />
      );
    case 'error':
      return <ErrorState onRetry={onRetry} />;
    case 'ready':
      return null;
  }
}
