// defineConfig vem de vitest/config, nao de vite: so essa versao
// reconhece a chave "test" na configuracao.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: Number(process.env['FRONTEND_PORT'] ?? 5174),
    strictPort: true,
    // Espelha o que o nginx faz em container: mantem frontend e backend na
    // mesma origem tambem em desenvolvimento, para que o cookie de sessao
    // SameSite=Lax seja enviado sem precisar de CORS com credenciais.
    proxy: {
      '/api/v1': {
        target: process.env['BACKEND_ORIGIN'] ?? 'http://localhost:8081',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/setupTests.ts'],
    css: false,
  },
});
