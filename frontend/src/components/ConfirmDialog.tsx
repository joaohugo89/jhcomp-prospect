import { useEffect, useId, useRef } from 'react';

import { buttonDanger, buttonPrimary, buttonSecondary } from './styles';

interface ConfirmDialogProps {
  readonly title: string;
  readonly description: string;
  /** Sem rótulo, o diálogo só informa: fica apenas o botão de fechar. */
  readonly confirmLabel?: string;
  readonly destructive?: boolean;
  readonly busy?: boolean;
  /** Confirmar fica bloqueado: por exemplo, até digitar o nome do que será excluído. */
  readonly confirmDisabled?: boolean;
  /** Conteúdo entre a descrição e os botões, como o campo de confirmação. */
  readonly children?: React.ReactNode;
  readonly onConfirm?: () => void;
  readonly onCancel: () => void;
  /** Texto do botão seguro; "Cancelar" por padrão. */
  readonly cancelLabel?: string;
}

/**
 * Confirmação de ação destrutiva ou irreversível (doc 08 §12).
 *
 * Modal com foco inicial no botão de cancelar, que é a escolha segura, Esc
 * para fechar e foco preso dentro do diálogo enquanto ele estiver aberto.
 */
export function ConfirmDialog({
  title,
  description,
  confirmLabel,
  destructive = false,
  busy = false,
  confirmDisabled = false,
  children,
  onConfirm,
  onCancel,
  cancelLabel = 'Cancelar',
}: ConfirmDialogProps): React.JSX.Element {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement;
    cancelRef.current?.focus();
    return () => {
      // Devolve o foco a quem abriu o diálogo.
      if (previouslyFocused instanceof HTMLElement) {
        previouslyFocused.focus();
      }
    };
  }, []);

  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      onCancel();
      return;
    }
    if (event.key !== 'Tab' || dialogRef.current === null) {
      return;
    }
    const focusable = dialogRef.current.querySelectorAll<HTMLElement>('input:not([disabled]), button:not([disabled])');
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (first === undefined || last === undefined) {
      return;
    }
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-oxblood-deep/70 px-4">
      {/* Teclado no próprio diálogo é o padrão de modal do WAI-ARIA: Esc fecha e Tab fica preso dentro. */}
      {/* oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions */}
      <div
        ref={dialogRef}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        onKeyDown={handleKeyDown}
        className="w-full max-w-md rounded-lg border border-stone-200 glass-panel p-6 shadow-2xl"
      >
        <h2 id={titleId} className="text-lg font-semibold text-stone-900">
          {title}
        </h2>
        <p id={descriptionId} className="mt-2 text-sm text-stone-700">
          {description}
        </p>
        {children}
        <div className="mt-6 flex justify-end gap-2">
          <button ref={cancelRef} type="button" className={buttonSecondary} onClick={onCancel} disabled={busy}>
            {cancelLabel}
          </button>
          {confirmLabel !== undefined && (
            <button
              type="button"
              className={destructive ? buttonDanger : buttonPrimary}
              onClick={onConfirm}
              disabled={busy || confirmDisabled}
            >
              {busy ? 'Aguarde…' : confirmLabel}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
