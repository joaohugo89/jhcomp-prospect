import { defineConfig, devices } from '@playwright/test';

/**
 * Testes end-to-end (doc 10 §2, D17): dois fluxos contra o ambiente completo
 * em container, pela mesma porta que o operador usa (nginx, 8090). Rodam no
 * navegador de verdade, com a CSP e os cookies reais: e o que pega erro que o
 * MockMvc e a API simulada nao veem, como o 403 de CSRF so no navegador.
 *
 *   docker compose up -d --build
 *   E2E_ADMIN_PASSWORD=... npm run e2e
 */
export default defineConfig({
  testDir: './e2e',
  // *.e2e.ts, e nao *.spec.ts: o Vitest recolheria *.spec.ts como teste unitario.
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  forbidOnly: process.env['CI'] !== undefined,
  retries: process.env['CI'] === undefined ? 0 : 1,
  timeout: 60_000,
  reporter: process.env['CI'] === undefined ? 'list' : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8090',
    // Ensaio local do compose de producao: o Caddy usa certificado da propria
    // CA para "localhost". Nunca ligar contra o dominio real.
    ignoreHTTPSErrors: process.env['E2E_IGNORE_HTTPS_ERRORS'] === 'true',
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
