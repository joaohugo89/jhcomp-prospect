import { expect, test } from '@playwright/test';

import { collectCspViolations, fictitiousPhone, importCompanyWithLead, login, uniqueSuffix } from './support';

/** Flow 1 of D17: log in, import a CSV, qualify, and move on the pipeline. */
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

  // Qualify from the lead detail (the properties card in the sidebar).
  const status = page.getByRole('region', { name: 'Propriedades' });
  await status.getByLabel('Novo status').selectOption('QUALIFIED');
  await status.getByRole('button', { name: 'Alterar status' }).click();
  await expect(status.getByLabel('Novo status').locator('option[value="CONTACT_APPROVED"]')).toHaveCount(1);

  // Move on the pipeline through the button (the keyboard path), without dragging.
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
