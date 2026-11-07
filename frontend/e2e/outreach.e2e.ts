import { expect, test } from '@playwright/test';

import { collectCspViolations, fictitiousPhone, importCompanyWithLead, login, uniqueSuffix } from './support';

/**
 * Flow 2 of D17: generate and approve a message, register a do-not-contact
 * request, and confirm a new approach is blocked, both on screen and on the
 * server (rule 4).
 */
test('gera e aprova mensagem; pedido de nao contato bloqueia nova abordagem', async ({ page }) => {
  const violations = collectCspViolations(page);
  const suffix = uniqueSuffix();
  const phone = fictitiousPhone();
  const company = { name: `Oficina E2E ${suffix}`, phone: phone.formatted, email: `e2e-${suffix}@example.test` };

  await login(page);
  await importCompanyWithLead(page, company);
  const leadUrl = page.url();
  const leadId = leadUrl.split('/').pop() ?? '';

  // A draft with no analysis starts in review, and approving requires
  // confirming that review. Messages live in a tab of the detail.
  await page.getByRole('tab', { name: 'Mensagens' }).click();
  const messages = page.getByRole('region', { name: 'Mensagens' });
  await messages.getByRole('button', { name: 'Gerar rascunho' }).click();
  await expect(messages.getByText('Precisa de revisão')).toBeVisible();
  const approve = messages.getByRole('button', { name: 'Aprovar' });
  await expect(approve).toBeDisabled();
  await messages.getByLabel(/Revisei estas afirmações/).check();
  await approve.click();
  await expect(messages.getByText('Aprovada, não enviada')).toBeVisible();
  await expect(messages.getByRole('form', { name: 'Registrar envio' })).toBeVisible();

  // A do-not-contact request by phone, in a different format from the one on file.
  await page.getByRole('link', { name: 'Não contato' }).click();
  const register = page.getByRole('region', { name: 'Registrar pedido de não contato' });
  await register.getByRole('group', { name: 'Tipo de contato' }).getByText('Telefone').click();
  await register.getByLabel(/^Contato/).fill(phone.e164);
  await register.getByLabel(/^Motivo/).fill('Pediu para não receber contato (teste E2E)');
  await register.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(page.getByText(/Pedido registrado/)).toBeVisible();

  // The check, which any member can run, already shows the block, in the stored format.
  const check = page.getByRole('region', { name: 'Conferir um contato' });
  await check.getByLabel('Contato a conferir').fill(phone.e164);
  await check.getByRole('button', { name: 'Conferir' }).click();
  await expect(check.getByText(/Bloqueado em todos os canais/)).toBeVisible();

  // On screen: the channel is blocked, there is nothing to generate, and recording a send disappears.
  await page.goto(leadUrl);
  await page.getByRole('tab', { name: 'Mensagens' }).click();
  await expect(messages.getByText(/Há pedido de não contato/)).toBeVisible();
  await expect(messages.getByRole('button', { name: 'Gerar novo rascunho' })).toBeDisabled();
  await expect(messages.getByRole('form', { name: 'Registrar envio' })).toHaveCount(0);

  // On the server: the rule does not depend on the screen. E-mail is blocked
  // too, because the company's phone is blocked on every channel (D26).
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'JH_XSRF')?.value ?? '';
  const response = await page.request.post(`/api/v1/leads/${leadId}/messages/generate`, {
    headers: { 'X-JH-XSRF': csrf },
    data: { channel: 'EMAIL' },
  });
  expect(response.status()).toBe(409);

  expect(violations).toEqual([]);
});
