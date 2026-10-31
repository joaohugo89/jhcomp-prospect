import { expect, test } from '@playwright/test';

import { collectCspViolations, fictitiousPhone, importCompanyWithLead, login, uniqueSuffix } from './support';

/** Fluxo 1 da D17: login, importar CSV, qualificar e mover no pipeline. */
test('importa empresa por CSV, qualifica o lead e move no pipeline', async ({ page }) => {
  const violations = collectCspViolations(page);
  const suffix = uniqueSuffix();
  const company = {
    name: `Padaria E2E ${suffix}`,
    phone: fictitiousPhone().formatted,
    email: `e2e-${suffix}@example.test`,
  };

  await login(page);
  await importCompanyWithLead(page, company);

  // Qualificar pelo detalhe do lead.
  const status = page.getByRole('region', { name: 'Status' });
  await status.getByLabel('Novo status').selectOption('QUALIFIED');
  await status.getByRole('button', { name: 'Alterar status' }).click();
  await expect(status.getByLabel('Novo status').locator('option[value="CONTACT_APPROVED"]')).toHaveCount(1);

  // Mover no pipeline, pelo botao (caminho do teclado), sem arrastar.
  await page.getByRole('link', { name: 'Pipeline' }).click();
  await page.getByLabel('Buscar empresa').fill(company.name);
  await page.getByRole('button', { name: 'Filtrar' }).click();

  const qualified = page.getByRole('region', { name: /^Qualificado/ });
  await expect(qualified.getByRole('link', { name: company.name })).toBeVisible();
  await qualified.getByRole('button', { name: `Mover ${company.name}` }).click();
  await qualified.getByLabel('Novo status').selectOption('CONTACT_APPROVED');
  await qualified.getByRole('button', { name: 'Alterar status' }).click();

  const approved = page.getByRole('region', { name: /^Contato aprovado/ });
  await expect(approved.getByRole('link', { name: company.name })).toBeVisible();
  await expect(qualified.getByRole('link', { name: company.name })).toHaveCount(0);

  expect(violations).toEqual([]);
});
