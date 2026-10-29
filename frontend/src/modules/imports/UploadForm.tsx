import { useState } from 'react';

import { IMPORT_FIELD_LABEL } from '../../app/labels';
import { navigate } from '../../app/router';
import { FormField } from '../../components/FormField';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { analyzeCsv } from '../../services/importService';
import { IMPORT_FIELDS, REQUIRED_IMPORT_FIELDS, type ImportField } from '../../types/import';
import { readHeaders } from './csvHeaders';
import { missingRequiredFields, uploadErrorMessage } from './importMessages';

export function UploadForm(): React.JSX.Element {
  const [file, setFile] = useState<File | null>(null);
  const [sourceName, setSourceName] = useState('');
  const [datasetNote, setDatasetNote] = useState('');
  const [headers, setHeaders] = useState<readonly string[]>([]);
  const [mapping, setMapping] = useState<Partial<Record<ImportField, string>>>({});
  const [showMapping, setShowMapping] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function chooseFile(chosen: File | null): Promise<void> {
    setFile(chosen);
    setError(null);
    setMapping({});
    setShowMapping(false);
    setHeaders(chosen === null ? [] : await readHeaders(chosen));
  }

  async function submit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (file === null) {
      setError('Escolha um arquivo CSV.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const batch = await analyzeCsv({ file, sourceName, datasetNote, mapping: showMapping ? mapping : {} });
      navigate(`/imports/${batch.id}`);
    } catch (cause: unknown) {
      setError(uploadErrorMessage(cause));
      if (missingRequiredFields(cause).length > 0) {
        setShowMapping(true);
      }
      setBusy(false);
    }
  }

  const missingRequired = [...REQUIRED_IMPORT_FIELDS].filter((field) => mapping[field] === undefined);

  return (
    <form
      noValidate
      className="space-y-4 rounded-lg border border-slate-200 bg-white p-6"
      onSubmit={(event) => {
        void submit(event);
      }}
    >
      <div>
        <label htmlFor="csv-file" className="block text-sm font-medium text-slate-700">
          Arquivo CSV
        </label>
        <input
          id="csv-file"
          type="file"
          accept=".csv,text/csv"
          aria-describedby="csv-file-hint"
          className="mt-1 block w-full text-sm"
          onChange={(event) => {
            void chooseFile(event.target.files?.item(0) ?? null);
          }}
        />
        <p id="csv-file-hint" className="mt-1 text-xs text-slate-500">
          UTF-8, até 2 MB e 2.000 linhas. Colunas obrigatórias: nome, cidade e UF. No Excel, use “Salvar como” e
          escolha “CSV UTF-8”.
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <FormField
          label="Origem dos dados"
          value={sourceName}
          onChange={setSourceName}
          maxLength={120}
          hint="De onde veio a planilha. Sem preencher, fica o nome do arquivo."
        />
        <FormField
          label="Termos ou observação da base"
          value={datasetNote}
          onChange={setDatasetNote}
          maxLength={1000}
          hint="Condições de uso da fonte, quando houver (RF-022)."
        />
      </div>

      {headers.length > 0 && !showMapping && (
        <button
          type="button"
          className={`${link} text-sm`}
          onClick={() => {
            setShowMapping(true);
          }}
        >
          Indicar as colunas manualmente
        </button>
      )}

      {showMapping && headers.length > 0 && (
        <fieldset className="grid gap-3 border-t border-slate-200 pt-4 md:grid-cols-3">
          <legend className="text-sm font-medium text-slate-800">Colunas do arquivo</legend>
          <p className="text-xs text-slate-500 md:col-span-3">
            Indique a coluna de cada campo. Campos sem coluna ficam em branco; colunas não usadas são ignoradas.
          </p>
          {IMPORT_FIELDS.map((field) => (
            <label key={field} className="text-sm font-medium text-slate-700">
              {IMPORT_FIELD_LABEL[field]}
              {REQUIRED_IMPORT_FIELDS.has(field) && <span aria-hidden="true"> *</span>}
              <select
                className={input}
                value={mapping[field] ?? ''}
                onChange={(event) => {
                  const header = event.target.value;
                  setMapping((current) => {
                    const next = { ...current };
                    if (header === '') {
                      delete next[field];
                    } else {
                      next[field] = header;
                    }
                    return next;
                  });
                }}
              >
                <option value="">Não importar</option>
                {headers.map((header) => (
                  <option key={header} value={header}>
                    {header}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </fieldset>
      )}

      <div aria-live="polite">
        {error !== null && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
            {error}
          </p>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          className={buttonPrimary}
          disabled={busy || file === null || (showMapping && missingRequired.length > 0)}
        >
          {busy ? 'Analisando…' : 'Analisar arquivo'}
        </button>
        {showMapping && (
          <button
            type="button"
            className={buttonSecondary}
            onClick={() => {
              setShowMapping(false);
              setMapping({});
            }}
          >
            Reconhecer colunas automaticamente
          </button>
        )}
      </div>
    </form>
  );
}
