import { FileSpreadsheet, UploadCloud, X } from 'lucide-react';
import { useId, useState } from 'react';

import { IMPORT_FIELD_LABEL } from '../../app/labels';
import { navigate } from '../../app/router';
import { FormField } from '../../components/FormField';
import { buttonPrimary, buttonSecondary, input, link } from '../../components/styles';
import { analyzeCsv } from '../../services/importService';
import { IMPORT_FIELDS, REQUIRED_IMPORT_FIELDS, type ImportField } from '../../types/import';
import { readHeaders } from './csvHeaders';
import { ImportSteps } from './ImportVisuals';
import { missingRequiredFields, uploadErrorMessage } from './importMessages';

/** Tamanho legível: "18 KB", "1,2 MB". */
function fileSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}

/** Primeira etapa: escolher o arquivo (clicando ou arrastando) e dizer de onde ele veio. */
export function UploadForm(): React.JSX.Element {
  const inputId = useId();
  const [dragging, setDragging] = useState(false);
  // Trocar a chave recria o campo de arquivo: limpar a escolha sem mexer no DOM.
  const [inputKey, setInputKey] = useState(0);
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
      className="space-y-5 rounded-2xl border border-slate-200/70 bg-white p-5 shadow-sm sm:p-6"
      onSubmit={(event) => {
        void submit(event);
      }}
    >
      <ImportSteps current={0} />

      {file === null ? (
        // Soltar arquivo não tem equivalente de teclado; o mesmo campo abre pelo clique ou pelo Enter.
        // oxlint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
        <label
          htmlFor={inputId}
          className={`flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-6 py-8 text-center transition-colors focus-within:outline-2 focus-within:outline-brand-500 ${
            dragging ? 'border-brand-500 bg-brand-50' : 'border-slate-300 bg-slate-50/60 hover:bg-slate-50'
          }`}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => {
            setDragging(false);
          }}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            void chooseFile(event.dataTransfer.files.item(0));
          }}
        >
          <UploadCloud aria-hidden="true" className="size-8 text-brand-600" />
          <span className="text-sm font-semibold text-slate-900">Arraste o CSV aqui ou clique para escolher</span>
          <span id="csv-file-hint" className="text-xs text-slate-500">
            UTF-8, até 2 MB e 2.000 linhas. Obrigatórias: nome, cidade e UF. No Excel: “Salvar como” → “CSV UTF-8”.
          </span>
          <input
            key={inputKey}
            id={inputId}
            type="file"
            accept=".csv,text/csv"
            aria-label="Arquivo CSV"
            aria-describedby="csv-file-hint"
            className="sr-only"
            onChange={(event) => {
              void chooseFile(event.target.files?.item(0) ?? null);
            }}
          />
        </label>
      ) : (
        <div className="flex items-center gap-3 rounded-xl bg-slate-50 px-4 py-3 ring-1 ring-slate-200">
          <FileSpreadsheet aria-hidden="true" className="size-8 shrink-0 text-emerald-600" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-slate-900">{file.name}</p>
            <p className="text-xs text-slate-500">
              {`${fileSize(file.size)}${headers.length > 0 ? ` · ${headers.length} colunas: ${headers.slice(0, 4).join(', ')}${headers.length > 4 ? '…' : ''}` : ''}`}
            </p>
          </div>
          <button
            type="button"
            aria-label="Trocar arquivo"
            className="rounded-lg p-1.5 text-slate-500 hover:bg-white hover:text-slate-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            onClick={() => {
              void chooseFile(null);
              setInputKey((value) => value + 1);
            }}
          >
            <X aria-hidden="true" className="size-4" />
          </button>
        </div>
      )}

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
