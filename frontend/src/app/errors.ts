import { HttpError } from '../services/httpClient';

/**
 * Mensagens de erro para o operador. Úteis e sem detalhe interno (doc 08 §11):
 * o texto técnico da API nunca é exibido como está.
 */

const FIELD_MESSAGES: readonly [RegExp, string][] = [
  [/must not be blank|must not be null/, 'Campo obrigatório.'],
  [/http or https URL/, 'Informe um endereço http ou https válido.'],
  [/phone number/, 'Informe um telefone válido, com DDD.'],
  [/two-letter state/, 'Use a sigla do estado, como MG.'],
  [/email address/, 'Informe um e-mail válido.'],
  [/size must be between/, 'Texto longo demais.'],
  [/past or in the present/, 'A data não pode estar no futuro.'],
  [
    /more cells than the header/,
    'A linha tem mais colunas que o cabeçalho. Verifique delimitadores dentro de valores sem aspas.',
  ],
];

export function fieldMessage(message: string): string {
  return FIELD_MESSAGES.find(([pattern]) => pattern.test(message))?.[1] ?? 'Valor inválido.';
}

/** Erros por campo, já traduzidos. Vazio quando o erro não é de validação. */
export function fieldErrorsOf(cause: unknown): ReadonlyMap<string, string> {
  if (!(cause instanceof HttpError)) {
    return new Map();
  }
  return new Map([...cause.fieldErrors].map(([field, message]) => [field, fieldMessage(message)]));
}

/**
 * Mensagem geral de uma falha de escrita.
 *
 * @param conflict texto para 409, que depende da operação
 */
export function actionErrorMessage(
  cause: unknown,
  conflict = 'A operação conflita com o estado atual do registro.',
): string {
  if (!(cause instanceof HttpError)) {
    return 'Não foi possível falar com a API. Verifique a conexão e tente novamente.';
  }
  switch (cause.type) {
    case 'VALIDATION_ERROR':
      return cause.fieldErrors.size > 0 ? 'Corrija os campos destacados.' : 'Os dados enviados não são válidos.';
    case 'CONFLICT':
      return conflict;
    case 'RESOURCE_NOT_FOUND':
      return 'Registro não encontrado. Ele pode ter sido removido.';
    case 'FORBIDDEN':
      return 'Seu papel nesta organização não permite esta operação.';
    case 'TOO_MANY_REQUESTS':
      return 'Muitas requisições. Aguarde alguns instantes e tente novamente.';
    case 'AI_PROVIDER_ERROR':
      return 'O rascunho não pôde ser gerado agora. Nada foi salvo; tente novamente em instantes.';
    default:
      return 'Não foi possível concluir a operação. Tente novamente em instantes.';
  }
}
