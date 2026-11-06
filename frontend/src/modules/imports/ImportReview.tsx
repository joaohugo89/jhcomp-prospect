import { useState } from 'react';

import { actionErrorMessage } from '../../app/errors';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { buttonPrimary, buttonSecondary } from '../../components/styles';
import { confirmImport, discardImport } from '../../services/importService';
import { type DuplicateChoice, type ImportBatch } from '../../types/import';

export function ImportReview({
  batch,
  choices,
  onDone,
}: {
  readonly batch: ImportBatch;
  readonly choices: ReadonlyMap<number, DuplicateChoice>;
  readonly onDone: () => void;
}): React.JSX.Element {
  const [createLeads, setCreateLeads] = useState(false);
  const [dialog, setDialog] = useState<'confirm' | 'discard' | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const rowsWith = (choice: DuplicateChoice): number[] =>
    [...choices]
      .filter(([, value]) => value === choice)
      .map(([row]) => row)
      .toSorted((a, b) => a - b);
  const asNew = rowsWith('new');
  const toMerge = rowsWith('merge');
  const toCreate = batch.validRows + asNew.length;
  const summary = describeOutcome(toCreate, toMerge.length);

  async function run(action: 'confirm' | 'discard'): Promise<void> {
    setBusy(true);
    setError(null);
    try {
      if (action === 'confirm') {
        await confirmImport(batch.id, asNew, toMerge, createLeads);
      } else {
        await discardImport(batch.id);
      }
      setDialog(null);
      onDone();
    } catch (cause: unknown) {
      setDialog(null);
      setError(actionErrorMessage(cause, 'Esta importação já foi processada. Recarregue a página.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="revisao" className="mb-6 rounded-2xl border border-brand-500/30 bg-brand-50 p-4">
      <h2 id="revisao" className="text-base font-semibold">
        Revisar e importar
      </h2>
      <p className="mt-1 text-sm text-stone-700">
        Serão importadas as linhas válidas e as possíveis duplicatas em que você escolher o que fazer. Completar uma
        empresa existente só preenche os campos vazios dela; nada que já está cadastrado é alterado. Rejeitadas nunca
        são importadas.
      </p>
      <label className="mt-3 flex items-center gap-2 text-sm text-stone-800">
        <input
          type="checkbox"
          checked={createLeads}
          onChange={(event) => {
            setCreateLeads(event.target.checked);
          }}
        />
        Abrir um lead para cada empresa, se ela ainda não tiver oportunidade aberta
      </label>
      <div className="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          className={buttonPrimary}
          disabled={toCreate + toMerge.length === 0 || busy}
          onClick={() => {
            setDialog('confirm');
          }}
        >
          Confirmar: {summary}
        </button>
        <button
          type="button"
          className={buttonSecondary}
          disabled={busy}
          onClick={() => {
            setDialog('discard');
          }}
        >
          Descartar
        </button>
      </div>
      {error !== null && (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {dialog === 'confirm' && (
        <ConfirmDialog
          title="Confirmar a importação?"
          description={`Resultado: ${summary}. A origem registrada será "${batch.sourceName}"${createLeads ? ', com um lead novo para cada empresa sem oportunidade aberta' : ''}. A importação não pode ser repetida a partir desta tela.`}
          confirmLabel="Importar"
          busy={busy}
          onConfirm={() => {
            void run('confirm');
          }}
          onCancel={() => {
            setDialog(null);
          }}
        />
      )}
      {dialog === 'discard' && (
        <ConfirmDialog
          title="Descartar a importação?"
          description="Nada será importado e os dados das linhas serão apagados. Para importar depois, envie o arquivo de novo."
          confirmLabel="Descartar"
          destructive
          busy={busy}
          onConfirm={() => {
            void run('discard');
          }}
          onCancel={() => {
            setDialog(null);
          }}
        />
      )}
    </section>
  );
}

function describeOutcome(toCreate: number, toMerge: number): string {
  const created = `${toCreate} ${toCreate === 1 ? 'empresa nova' : 'empresas novas'}`;
  if (toMerge === 0) {
    return created;
  }
  return `${created} e ${toMerge} ${toMerge === 1 ? 'completada' : 'completadas'}`;
}
