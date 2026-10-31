import type { Load } from '../app/useLoad';
import { buttonSecondary } from './styles';

/** Estados de interface do doc 08 §11. Mensagens úteis, sem detalhe interno. */

export function LoadingState({ label = 'Carregando…' }: { readonly label?: string }): React.JSX.Element {
  return (
    <p role="status" className="py-8 text-center text-sm text-slate-600">
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
    <div className="rounded-lg border border-dashed border-slate-300 bg-white px-6 py-10 text-center">
      <p className="font-medium text-slate-800">{title}</p>
      {description !== undefined && <p className="mt-1 text-sm text-slate-600">{description}</p>}
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
 * Renderiza os estados que não são "pronto", ou nada.
 *
 * Centraliza a decisão para que nenhuma tela esqueça um caso: quem usa só
 * precisa cuidar do dado carregado.
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
      // Recurso de outra organização também chega aqui: a API responde 404
      // de propósito, e a tela não sugere que ele exista.
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
