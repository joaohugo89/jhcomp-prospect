import { expect, type Page } from '@playwright/test';

/**
 * Apoio dos testes E2E. Todo dado criado aqui e ficticio (doc 10 §10) e leva
 * sufixo unico: o banco do ambiente persiste entre execucoes, e nome ou
 * telefone repetido viraria duplicata ou bloqueio de uma execucao anterior.
 */

export function uniqueSuffix(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

/** Celular ficticio de MG com nove digitos aleatorios depois do DDD. */
export function fictitiousPhone(): { readonly formatted: string; readonly e164: string } {
  const digits = `9${String(Math.floor(Math.random() * 100_000_000)).padStart(8, '0')}`;
  return { formatted: `(35) ${digits.slice(0, 5)}-${digits.slice(5)}`, e164: `+5535${digits}` };
}

/**
 * Falha o teste se o navegador recusar recurso pela CSP: o cabecalho e de
 * producao, e uma violacao quebraria a tela sem nenhum teste unitario notar.
 */
export function collectCspViolations(page: Page): string[] {
  const violations: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error' && message.text().includes('Content Security Policy')) {
      violations.push(message.text());
    }
  });
  return violations;
}

export async function login(page: Page): Promise<void> {
  const email = process.env['E2E_ADMIN_EMAIL'] ?? 'admin@jhcomp.local';
  const password = process.env['E2E_ADMIN_PASSWORD'];
  if (password === undefined || password === '') {
    throw new Error('Defina E2E_ADMIN_PASSWORD com a senha do administrador do ambiente.');
  }
  await page.goto('/');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('button', { name: /Sair/ })).toBeVisible();
}

export interface FictitiousCompany {
  readonly name: string;
  readonly phone: string;
  readonly email: string;
}

/** Importa uma empresa por CSV, pela tela, abrindo lead; devolve na pagina do lead. */
export async function importCompanyWithLead(page: Page, company: FictitiousCompany): Promise<void> {
  const csv = `nome;cidade;uf;telefone;email\n${company.name};Cidade Exemplo;MG;${company.phone};${company.email}\n`;

  await page.goto('/imports');
  await page.getByLabel('Arquivo CSV').setInputFiles({
    name: 'empresas-e2e.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf-8'),
  });
  await page.getByRole('button', { name: 'Analisar arquivo' }).click();

  const review = page.getByRole('region', { name: 'Revisar e importar' });
  await expect(review).toBeVisible();
  await review.getByLabel(/Abrir um lead para cada empresa/).check();
  await review.getByRole('button', { name: /^Confirmar:/ }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Importar' }).click();

  await page.getByRole('link', { name: 'Ver lead' }).click();
  await expect(page.getByRole('heading', { level: 1, name: company.name })).toBeVisible();
}
