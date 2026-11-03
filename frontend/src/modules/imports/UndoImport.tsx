import { Undo2 } from 'lucide-react';
import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { formatDateTime } from '../../app/format';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { buttonSecondary } from '../../components/styles';
import { previewUndoImport, undoImport } from '../../services/importService';
import type { ImportBatch, UndoResult } from '../../types/import';

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

/**
 * Desfazer importação (D32): exclui as empresas que o lote criou e que
 * continuam sem atividade. Antes de confirmar, mostra a prévia do que sai e
 * do que fica. Quem importou, por 7 dias, ou o administrador.
 */
export function UndoImport({
  batch,
  onUndone,
}: {
  readonly batch: ImportBatch;
  readonly onUndone: () => void;
}): React.JSX.Element | null {
  const [preview, setPreview] = useState<UndoResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (batch.undoneAt !== undefined) {
    return (
      <p className="mb-6 rounded-md bg-slate-100 px-4 py-3 text-sm text-slate-700">
        {`Importação desfeita em ${formatDateTime(batch.undoneAt)}. As linhas cujas empresas foram excluídas aparecem como "Excluídas depois".`}
      </p>
    );
  }
  if (batch.status !== 'COMPLETED' || batch.importedRows === 0) {
    return null;
  }

  async function open(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      setPreview(await previewUndoImport(batch.id));
    } catch (cause: unknown) {
      setError(actionErrorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  async function confirm(): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      await undoImport(batch.id);
      setPreview(null);
      onUndone();
    } catch (cause: unknown) {
      setError(
        actionErrorMessage(cause, 'Só quem importou, em até 7 dias, ou o administrador pode desfazer esta importação.'),
      );
      setPreview(null);
    } finally {
      setBusy(false);
    }
  }

  const kept: string[] = [];
  if (preview !== null && preview.keptWithActivity > 0) {
    kept.push(
      preview.keptWithActivity === 1
        ? '1 empresa já tem atividade e fica'
        : `${preview.keptWithActivity} empresas já têm atividade e ficam`,
    );
  }
  if (preview !== null && preview.mergedUntouched > 0) {
    kept.push(
      preview.mergedUntouched === 1
        ? '1 empresa que já existia e foi completada não muda'
        : `${preview.mergedUntouched} empresas que já existiam e foram completadas não mudam`,
    );
  }

  return (
    <div className="mb-6">
      <button
        type="button"
        className={`${buttonSecondary} gap-1.5`}
        disabled={busy}
        onClick={() => {
          void open();
        }}
      >
        <Undo2 aria-hidden="true" className="size-4" />
        Desfazer importação
      </button>
      {error !== null && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {preview !== null && (
        <ConfirmDialog
          title="Desfazer esta importação?"
          description={
            preview.removed === 0
              ? `Nenhuma empresa seria excluída: todas já têm atividade ou foram excluídas.${kept.length === 0 ? '' : ` ${kept.join('; ')}.`}`
              : `${plural(preview.removed, 'empresa criada', 'empresas criadas')} por ela, sem atividade, ${
                  preview.removed === 1 ? 'será excluída' : 'serão excluídas'
                } com o lead aberto junto, sem como desfazer.${kept.length === 0 ? '' : ` ${kept.join('; ')}.`}`
          }
          confirmLabel="Desfazer importação"
          destructive
          busy={busy}
          confirmDisabled={preview.removed === 0}
          onConfirm={() => {
            void confirm();
          }}
          onCancel={() => {
            setPreview(null);
          }}
        />
      )}
    </div>
  );
}
