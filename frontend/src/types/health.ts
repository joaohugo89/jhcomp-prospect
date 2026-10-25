/** Resposta de GET /health. Espelha o record do backend. */
export interface Health {
  readonly status: string;
  readonly version: string;
  readonly checkedAt: string;
}
