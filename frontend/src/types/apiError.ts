/**
 * Formato de erro da API (doc 05 §1), compativel com RFC 7807 ProblemDetail.
 */
export interface FieldError {
  readonly field: string;
  readonly message: string;
}

export interface ProblemDetail {
  readonly type?: string;
  readonly title?: string;
  readonly status?: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly FieldError[];
}

/** Erro de API normalizado consumido pela interface. */
export interface ApiError {
  readonly status: number;
  readonly type: string;
  readonly message: string;
  readonly detail: string | undefined;
  readonly fieldErrors: ReadonlyMap<string, string>;
}
