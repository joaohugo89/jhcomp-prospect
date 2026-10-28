import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { COMPANY_ID, company, fakeApi, json, page } from '../../test/fakeApi';
import { CompaniesPage } from './CompaniesPage';
import { CompanyDetailPage } from './CompanyDetailPage';
import { CompanyNewPage } from './CompanyNewPage';

  function detailRoutes(overrides: Record<string, () => Response> = {}) {
    return fakeApi({
      'GET /companies/': () => json(company),
      'GET /companies/11111111-1111-4111-8111-111111111111/sources': () =>
        json(page([{ id: 's-1', type: 'MANUAL', name: 'Cadastro manual', collectedAt: '2026-11-01T12:00:00Z' }])),
      'GET /companies/11111111-1111-4111-8111-111111111111/duplicates': () =>
        json([
          { companyId: 'c-2', name: 'Barbearia Ficticia Centro', city: 'Cidade Exemplo', state: 'MG', archived: false, signals: ['DOMAIN'] },
        ]),
      ...overrides,
    });
  }

describe('Empresas', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/companies');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('lista', () => {
    it('envia so os filtros preenchidos e volta a primeira pagina', async () => {
      const calls = fakeApi({ 'GET /companies': () => json(page([company])) });

      render(<CompaniesPage />);
      await screen.findByRole('link', { name: 'Barbearia Ficticia' });

      await userEvent.type(screen.getByLabelText('Buscar por nome'), '  salão ');
      await userEvent.click(screen.getByLabelText('Incluir arquivadas'));
      await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));

      await waitFor(() => {
        expect(calls).toHaveLength(2);
      });
      const query = calls[1]?.query;
      expect(query?.get('search')).toBe('salão');
      expect(query?.get('includeArchived')).toBe('true');
      expect(query?.get('page')).toBe('0');
      expect(query?.has('segment')).toBe(false);
    });

    it('diferencia lista vazia de filtro sem resultado', async () => {
      fakeApi({ 'GET /companies': () => json(page([])) });

      render(<CompaniesPage />);

      expect(await screen.findByText('Nenhuma empresa cadastrada ainda.')).toBeInTheDocument();
      await userEvent.type(screen.getByLabelText('Cidade'), 'Outra');
      await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));
      expect(await screen.findByText('Nenhuma empresa corresponde aos filtros.')).toBeInTheDocument();
    });

    it('mostra erro com opcao de tentar de novo', async () => {
      let attempts = 0;
      fakeApi({
        'GET /companies': () => {
          attempts += 1;
          return attempts === 1 ? json({ type: 'UNEXPECTED_ERROR', status: 500 }, 500) : json(page([company]));
        },
      });

      render(<CompaniesPage />);

      const alert = await screen.findByRole('alert');
      expect(alert).not.toHaveTextContent('500');
      await userEvent.click(within(alert).getByRole('button', { name: 'Tentar novamente' }));
      expect(await screen.findByRole('link', { name: 'Barbearia Ficticia' })).toBeInTheDocument();
    });
  });

  describe('cadastro', () => {
    it('associa o erro do servidor ao campo, em portugues', async () => {
      fakeApi({
        'POST /companies': () =>
          json(
            {
              type: 'VALIDATION_ERROR',
              status: 400,
              errors: [
                { field: 'websiteUrl', message: 'must be an http or https URL' },
                { field: 'source.url', message: 'must be an http or https URL' },
              ],
            },
            400,
          ),
      });

      render(<CompanyNewPage />);
      await userEvent.type(screen.getByLabelText(/^Nome(?! comercial)/), 'Loja Ficticia');
      await userEvent.type(screen.getByLabelText('Site'), 'javascript:alert(1)');
      await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

      const site = screen.getByLabelText('Site');
      await waitFor(() => {
        expect(site).toHaveAttribute('aria-invalid', 'true');
      });
      expect(site).toHaveAccessibleDescription('Informe um endereço http ou https válido.');
      expect(screen.getByLabelText('URL da fonte')).toHaveAttribute('aria-invalid', 'true');
      expect(screen.getByRole('alert')).toHaveTextContent('Corrija os campos destacados.');
    });

    it('so envia a origem quando o operador a informa, e abre o detalhe', async () => {
      const calls = fakeApi({ 'POST /companies': () => json({ ...company, id: COMPANY_ID }, 201) });

      render(<CompanyNewPage />);
      await userEvent.type(screen.getByLabelText(/^Nome(?! comercial)/), 'Loja Ficticia');
      await userEvent.type(screen.getByLabelText(/^Cidade/), 'Cidade Exemplo');
      await userEvent.type(screen.getByLabelText(/^UF/), 'MG');
      await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

      await waitFor(() => {
        expect(window.location.pathname).toBe(`/companies/${COMPANY_ID}`);
      });
      expect(calls[0]?.body).toMatchObject({ name: 'Loja Ficticia', state: 'MG' });
      expect(calls[0]?.body).not.toHaveProperty('source');
    });
  });

  describe('detalhe', () => {

    it('mostra dados, origem e duplicatas, e monta link seguro para o site', async () => {
      detailRoutes();

      render(<CompanyDetailPage id={COMPANY_ID} />);

      const site = await screen.findByRole('link', { name: 'barbearia.example.test' });
      expect(site).toHaveAttribute('href', 'https://barbearia.example.test/');
      expect(site).toHaveAttribute('rel', 'noopener noreferrer');
      expect(await screen.findByText('Mesmo domínio')).toBeInTheDocument();
      expect(await screen.findByText('Cadastro manual')).toBeInTheDocument();
      // Ausente é "não informado", não "não existe" (doc 06 §5).
      expect(screen.getAllByText('Não informado').length).toBeGreaterThan(0);
    });

    it('nao gera link para URL com esquema perigoso vinda da API', async () => {
      detailRoutes({ 'GET /companies/': () => json({ ...company, websiteUrl: 'javascript:alert(1)' }) });

      render(<CompanyDetailPage id={COMPANY_ID} />);

      await screen.findByRole('heading', { level: 1, name: 'Barbearia Ficticia' });
      expect(screen.queryByRole('link', { name: 'javascript:alert(1)' })).not.toBeInTheDocument();
    });

    it('pede confirmacao antes de arquivar e nao arquiva ao cancelar', async () => {
      const calls = detailRoutes({ 'POST /companies/': () => json({ ...company, archived: true }) });

      render(<CompanyDetailPage id={COMPANY_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Arquivar' }));

      const dialog = screen.getByRole('alertdialog', { name: 'Arquivar empresa?' });
      // Foco começa na escolha segura.
      expect(within(dialog).getByRole('button', { name: 'Cancelar' })).toHaveFocus();
      await userEvent.keyboard('{Escape}');
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
      expect(calls.some((call) => call.method === 'POST')).toBe(false);

      await userEvent.click(screen.getByRole('button', { name: 'Arquivar' }));
      await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Arquivar' }));
      await waitFor(() => {
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/archive'))).toBe(true);
      });
    });

    it('explica quando a empresa ja tem oportunidade aberta', async () => {
      detailRoutes({ 'POST /leads': () => json({ type: 'CONFLICT', status: 409 }, 409) });

      render(<CompanyDetailPage id={COMPANY_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Abrir lead' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('já tem uma oportunidade aberta');
    });
  });
});
