import { X } from 'lucide-react';
import { useEffect, useRef } from 'react';

/**
 * A non-modal side panel (the lead or company preview beside the list).
 *
 * On desktop the list stays usable and clicking another row swaps the
 * content. On mobile the panel covers the list and tapping outside closes it
 * without triggering whatever is underneath. Esc closes and returns focus.
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
  /** The panel's name: the region label and the visible title at the top. */
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
        className="fixed inset-0 z-30 bg-oxblood-deep/60 lg:hidden"
        onClick={onClose}
      />
      <aside
        id={id}
        aria-label={label}
        // On mobile a strip is left on the left: that is where you tap to close.
        className="fixed inset-y-0 right-0 z-40 flex w-[calc(100%-3rem)] max-w-md flex-col border-l border-stone-200 glass-panel shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-stone-100 px-5 py-3">
          <p className="text-[11px] font-semibold tracking-wider text-stone-500 font-mono uppercase">{label}</p>
          <button
            type="button"
            aria-label={`Fechar ${label.toLowerCase()}`}
            className="rounded-lg p-1.5 text-stone-500 hover:bg-stone-100 hover:text-stone-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={onClose}
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer !== undefined && <div className="flex gap-2 border-t border-stone-100 px-5 py-3">{footer}</div>}
      </aside>
    </>
  );
}

/** One fact in the panel: a small label and a value; dimmed when the value is "none". */
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
    <div className="rounded-xl bg-stone-50 px-3 py-2.5 ring-1 ring-stone-200/70">
      <dt className="text-[11px] text-stone-500">{label}</dt>
      <dd
        className={`mt-0.5 text-sm font-semibold break-words ${muted ? 'font-normal text-stone-400' : (tone ?? 'text-stone-900')}`}
      >
        {children}
      </dd>
    </div>
  );
}
