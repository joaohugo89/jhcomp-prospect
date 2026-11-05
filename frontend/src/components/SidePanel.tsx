import { X } from 'lucide-react';
import { useEffect, useRef } from 'react';

/**
 * Painel lateral não modal (prévia de lead ou de empresa ao lado da lista).
 *
 * No desktop a lista continua usável e clicar em outra linha troca o
 * conteúdo. No celular o painel cobre a lista e tocar fora fecha, sem acionar
 * o que está embaixo. Esc fecha e devolve o foco a quem abriu.
 */
export function SidePanel({
  id,
  label,
  backdropTestId,
  footer,
  onClose,
  children,
}: {
  readonly id: string;
  /** Nome do painel: rótulo da região e título visível no topo. */
  readonly label: string;
  readonly backdropTestId: string;
  readonly footer?: React.ReactNode;
  readonly onClose: () => void;
  readonly children: React.ReactNode;
}): React.JSX.Element {
  const opener = useRef<Element | null>(null);

  useEffect(() => {
    opener.current = document.activeElement;
    return () => {
      if (opener.current instanceof HTMLElement) {
        opener.current.focus();
      }
    };
  }, []);
  useEffect(() => {
    function onKey(event: KeyboardEvent): void {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('keydown', onKey);
    };
  }, [onClose]);

  return (
    <>
      {/* oxlint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
      <div
        aria-hidden="true"
        data-testid={backdropTestId}
        className="fixed inset-0 z-30 bg-slate-900/30 lg:hidden"
        onClick={onClose}
      />
      <aside
        id={id}
        aria-label={label}
        // No celular sobra uma faixa à esquerda: é onde se toca para fechar.
        className="fixed inset-y-0 right-0 z-40 flex w-[calc(100%-3rem)] max-w-md flex-col border-l border-slate-200 bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
          <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">{label}</p>
          <button
            type="button"
            aria-label={`Fechar ${label.toLowerCase()}`}
            className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={onClose}
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer !== undefined && <div className="flex gap-2 border-t border-slate-100 px-5 py-3">{footer}</div>}
      </aside>
    </>
  );
}

/** Um fato do painel: rótulo pequeno e valor; apagado quando o valor é "não há". */
export function PanelFact({
  label,
  children,
  muted = false,
  tone,
}: {
  readonly label: string;
  readonly children: React.ReactNode;
  readonly muted?: boolean;
  readonly tone?: string | undefined;
}): React.JSX.Element {
  return (
    <div className="rounded-xl bg-slate-50 px-3 py-2.5 ring-1 ring-slate-200/70">
      <dt className="text-[11px] text-slate-500">{label}</dt>
      <dd
        className={`mt-0.5 text-sm font-semibold break-words ${muted ? 'font-normal text-slate-400' : (tone ?? 'text-slate-900')}`}
      >
        {children}
      </dd>
    </div>
  );
}
