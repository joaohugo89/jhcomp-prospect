import { actionErrorMessage } from '../../app/errors';
import { HttpError } from '../../services/httpClient';

/**
 * Messages for a rejected file, with what to do next.
 *
 * The API detail is never shown as it is; each known case becomes practical
 * guidance for whoever exported the spreadsheet.
 */
const UPLOAD_MESSAGES: readonly [RegExp, string][] = [
  [/UTF-8/, 'O arquivo precisa estar em UTF-8. No Excel, use “Salvar como” e escolha “CSV UTF-8”.'],
  [/must be a CSV/, 'Envie um arquivo .csv. No Excel, use “Salvar como” e escolha “CSV UTF-8”.'],
  [/2 MB/, 'O arquivo passa de 2 MB. Divida a planilha em partes menores.'],
  [/no data rows/, 'O arquivo só tem o cabeçalho, sem linhas de dados.'],
  [/limit of 2000 rows/, 'O arquivo passa de 2.000 linhas. Divida a planilha em partes menores.'],
  [/limit of 50 columns/, 'O arquivo passa de 50 colunas. Remova as colunas que não serão importadas.'],
  [/is empty|no header/, 'O arquivo está vazio.'],
  [/duplicated column/, 'O cabeçalho tem colunas com o mesmo nome. Renomeie uma delas.'],
  [/empty column name/, 'O cabeçalho tem uma coluna sem nome.'],
  [/not a valid CSV/, 'Não foi possível ler o CSV. Verifique se há aspas abertas sem fechamento.'],
  [
    /Column not found|more than one field|Unknown import field|mapping must be/,
    'Revise o mapeamento: escolha uma coluna diferente para cada campo.',
  ],
];

/** Required fields the backend did not find in the header, or empty. */
export function missingRequiredFields(cause: unknown): readonly string[] {
  if (!(cause instanceof HttpError) || cause.detail === undefined) {
    return [];
  }
  const match = /^Required fields without a column: (.+)$/.exec(cause.detail);
  return match?.[1]?.split(', ') ?? [];
}

export function uploadErrorMessage(cause: unknown): string {
  if (missingRequiredFields(cause).length > 0) {
    return 'Não encontramos as colunas obrigatórias. Indique abaixo qual coluna corresponde a cada campo.';
  }
  if (cause instanceof HttpError && cause.type === 'PAYLOAD_TOO_LARGE') {
    return 'O arquivo passa de 2 MB. Divida a planilha em partes menores.';
  }
  if (cause instanceof HttpError && cause.type === 'VALIDATION_ERROR' && cause.detail !== undefined) {
    const detail = cause.detail;
    const known = UPLOAD_MESSAGES.find(([pattern]) => pattern.test(detail));
    if (known !== undefined) {
      return known[1];
    }
  }
  return actionErrorMessage(cause);
}
