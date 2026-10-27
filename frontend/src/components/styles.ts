/**
 * Classes compartilhadas de controles. Centralizadas para que foco visível e
 * estado desabilitado sejam iguais em toda a aplicação (doc 08 §12).
 */

const focus = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500';

export const buttonPrimary = `inline-flex items-center justify-center rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const buttonSecondary = `inline-flex items-center justify-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const buttonDanger = `inline-flex items-center justify-center rounded-md bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-60 ${focus}`;

export const input = `mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 aria-[invalid=true]:border-red-600 ${focus}`;

export const link = `font-medium text-brand-700 underline-offset-2 hover:underline ${focus}`;
