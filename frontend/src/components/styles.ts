/**
 * Classes compartilhadas de controles. Centralizadas para que foco visível e
 * estado desabilitado sejam iguais em toda a aplicação (doc 08 §12).
 */

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500';

export const buttonPrimary = `inline-flex items-center justify-center rounded-lg bg-brand-600 px-3.5 py-1.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const buttonSecondary = `inline-flex items-center justify-center rounded-lg border border-stone-300 bg-white px-3.5 py-1.5 text-sm font-medium text-stone-800 hover:bg-stone-50 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const buttonDanger = `inline-flex items-center justify-center rounded-lg bg-red-700 px-3.5 py-1.5 text-sm font-medium text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const input = `mt-1 w-full rounded-lg border border-stone-300 bg-white px-2.5 py-1.5 text-sm text-stone-900 aria-[invalid=true]:border-red-600 ${focus}`;

export const link = `font-medium text-brand-700 underline-offset-2 hover:underline ${focus}`;

/** Superfície de cartão: linha fina sobre o creme, sem sombra, como nos sites da JH Comp. */
export const card = 'rounded-2xl border border-stone-200 bg-white';

/** Recorte de um clique (filtro rápido): escuro quando ativo; o estado vai também em `aria-pressed`. */
export function chip(active: boolean): string {
  return `h-8 rounded-full px-3 text-xs font-semibold ${focus} ${
    active ? 'bg-oxblood text-cream' : 'bg-white text-stone-600 ring-1 ring-stone-200 hover:bg-stone-50'
  }`;
}
