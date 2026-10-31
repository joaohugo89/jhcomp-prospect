import { expect, test } from '@playwright/test';

import { collectCspViolations, fictitiousPhone, importCompanyWithLead, login, uniqueSuffix } from './support';

/**
 * Fluxo 2 da D17: gerar e aprovar mensagem, registrar nao contato e confirmar
 * que nova abordagem e bloqueada, na tela e no servidor (regra 4).
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

  // Rascunho sem analise: nasce em revisao e aprovar exige confirmar a revisao.
  const messages = page.getByRole('region', { name: 'Mensagens' });
  await messages.getByRole('button', { name: 'Gerar rascunho' }).click();
  await expect(messages.getByText('Precisa de revisão')).toBeVisible();
  const approve = messages.getByRole('button', { name: 'Aprovar' });
  await expect(approve).toBeDisabled();
  await messages.getByLabel(/Revisei estas afirmações/).check();
  await approve.click();
  await expect(messages.getByText('Aprovada, não enviada')).toBeVisible();
  await expect(messages.getByRole('form', { name: 'Registrar envio' })).toBeVisible();

  // Pedido de nao contato pelo telefone, em outro formato que o cadastrado.
  await page.getByRole('link', { name: 'Não contato' }).click();
  await page.getByLabel('Tipo de contato').selectOption('PHONE');
  await page.getByLabel(/^Contato/).fill(phone.e164);
  await page.getByLabel(/^Motivo/).fill('Pediu para não receber contato (teste E2E)');
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(page.getByText(/Pedido registrado/)).toBeVisible();

  // Na tela: canal bloqueado, nada a gerar e o registro de envio some.
  await page.goto(leadUrl);
  await expect(messages.getByText(/Há pedido de não contato/)).toBeVisible();
  await expect(messages.getByRole('button', { name: 'Gerar novo rascunho' })).toBeDisabled();
  await expect(messages.getByRole('form', { name: 'Registrar envio' })).toHaveCount(0);

  // No servidor: a regra nao depende da tela. E-mail tambem fica bloqueado,
  // porque o telefone da empresa esta bloqueado em todos os canais (D26).
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'JH_XSRF')?.value ?? '';
  const response = await page.request.post(`/api/v1/leads/${leadId}/messages/generate`, {
    headers: { 'X-JH-XSRF': csrf },
    data: { channel: 'EMAIL' },
  });
  expect(response.status()).toBe(409);

  expect(violations).toEqual([]);
});
