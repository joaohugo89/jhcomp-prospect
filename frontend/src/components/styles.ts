/**
 * Classes compartilhadas de controles. Centralizadas para que foco visível e
 * estado desabilitado sejam iguais em toda a aplicação (doc 08 §12).
 *
 * O acabamento é o mesmo dos sites da JH Comp: vidro translúcido sobre o fundo
 * vinho, linha fina de creme e vermelho da marca na ação principal.
 */

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500';

export const buttonPrimary = `inline-flex items-center justify-center rounded-lg bg-brand-600 px-3.5 py-1.5 text-sm font-semibold text-white shadow-lg shadow-brand-500/20 hover:bg-brand-500 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const buttonSecondary = `inline-flex items-center justify-center rounded-lg border border-stone-300 glass px-3.5 py-1.5 text-sm font-medium text-stone-800 hover:bg-glass-lift disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const buttonDanger = `inline-flex items-center justify-center rounded-lg bg-red-600 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const input = `mt-1 w-full rounded-lg border border-stone-300 glass px-2.5 py-1.5 text-sm text-stone-900 placeholder:text-stone-400 aria-[invalid=true]:border-red-500 ${focus}`;

export const link = `font-medium text-brand-700 underline-offset-2 hover:underline ${focus}`;

/** Superfície de cartão: vidro com linha fina de creme, como nos sites da JH Comp. */
export const card = 'rounded-2xl border border-stone-200 glass shadow-xl shadow-oxblood-deep/30';

/** Recorte de um clique (filtro rápido): vermelho da marca quando ativo; o estado vai também em `aria-pressed`. */
export function chip(active: boolean): string {
  return `h-8 rounded-full px-3 text-xs font-semibold ${focus} ${
    active ? 'bg-brand-600 text-white' : 'glass text-stone-600 ring-1 ring-stone-200 hover:bg-glass-lift'
  }`;
}
