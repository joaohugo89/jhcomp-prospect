import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { COMPANY_ID, company, fakeApi, json, page } from '../../test/fakeApi';
import { CompaniesPage } from './CompaniesPage';
import { CompanyDetailPage } from './CompanyDetailPage';
import { CompanyNewPage } from './CompanyNewPage';

const C = `/companies/${COMPANY_ID}`;

const companyItem = {
  companyId: COMPANY_ID,
  name: 'Barbearia Ficticia',
  segment: 'Barbearia',
  city: 'Cidade Exemplo',
  state: 'MG',
  hasPhone: true,
  hasEmail: false,
  hasWebsite: true,
  hasInstagram: false,
  origin: 'MANUAL',
  leadCount: 0,
  possibleDuplicate: true,
  archived: false,
  createdAt: '2026-11-01T12:00:00Z',
};

function listRoutes(items: readonly unknown[], overrides: Record<string, () => Response> = {}) {
  return fakeApi({
    'GET /companies/worklist': () => json(page(items)),
    'GET /companies/segments': () => json({ segments: [{ name: 'Barbearia', companies: 3 }], withoutSegment: 1 }),
    [`GET ${C}/summary`]: () => json(companyItem),
    'GET /companies/': () => json(company),
    ...overrides,
  });
}

function detailRoutes(overrides: Record<string, () => Response> = {}) {
  return fakeApi({
    'GET /companies/': () => json(company),
    [`GET ${C}/summary`]: () => json(companyItem),
    [`GET ${C}/leads`]: () => json(page([])),
    [`GET ${C}/removal`]: () => json({ allowed: false, blocker: 'HAS_ACTIVITY' }),
    [`GET ${C}/sources`]: () =>
      json(page([{ id: 's-1', type: 'MANUAL', name: 'Cadastro manual', collectedAt: '2026-11-01T12:00:00Z' }])),
    [`GET ${C}/duplicates`]: () =>
      json([
        {
          companyId: 'c-2',
          name: 'Barbearia Ficticia Centro',
          city: 'Cidade Exemplo',
          state: 'MG',
          archived: false,
          signals: ['DOMAIN'],
        },
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
    it('mostra lead, contato e duplicata, envia filtros e grava na URL', async () => {
      const calls = listRoutes([companyItem]);

      render(<CompaniesPage />);
      expect(await screen.findByRole('button', { name: 'Barbearia Ficticia' })).toBeInTheDocument();
      expect(within(screen.getByRole('table')).getByText('Sem lead')).toBeInTheDocument();
      expect(screen.getByText('Duplicata?')).toBeInTheDocument();
      expect(screen.getByText('Contato: telefone, site')).toBeInTheDocument();

      await userEvent.type(screen.getByLabelText('Buscar por nome'), '  salão ');
      await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));
      await userEvent.click(screen.getByRole('button', { name: 'Sem contato' }));

      const listCalls = (): typeof calls => calls.filter((call) => call.path === '/companies/worklist');
      await waitFor(() => {
        expect(listCalls()).toHaveLength(3);
      });
      const query = listCalls()[2]?.query;
      expect(query?.get('search')).toBe('salão');
      expect(query?.get('focus')).toBe('WITHOUT_CONTACT');
      expect(window.location.search).toBe('?search=sal%C3%A3o&focus=WITHOUT_CONTACT');
      expect(screen.getByRole('button', { name: 'Sem contato' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('ordena pelo cabecalho', async () => {
      const calls = listRoutes([companyItem]);
      render(<CompaniesPage />);
      await userEvent.click(await screen.findByRole('button', { name: 'Empresa' }));
      await waitFor(() => {
        expect(
          calls
            .filter((call) => call.path === '/companies/worklist')
            .at(-1)
            ?.query.get('sort'),
        ).toBe('NAME');
      });
    });

    it('abre a previa ao clicar e, no celular, fecha ao tocar fora', async () => {
      listRoutes([companyItem]);
      render(<CompaniesPage />);
      await userEvent.click(await screen.findByRole('button', { name: 'Barbearia Ficticia' }));

      const preview = await screen.findByRole('complementary', { name: 'Prévia da empresa' });
      expect(await within(preview).findByRole('heading', { name: 'Barbearia Ficticia' })).toBeInTheDocument();
      expect(within(preview).getByText('Nenhum lead aberto ainda')).toBeInTheDocument();
      await userEvent.click(screen.getByTestId('fundo-previa-empresa'));
      expect(screen.queryByRole('complementary', { name: 'Prévia da empresa' })).not.toBeInTheDocument();
    });

    it('define segmento em lote para as selecionadas', async () => {
      const calls = listRoutes([companyItem], {
        'POST /companies/bulk': () => json({ updated: 1, skippedArchived: 0 }),
      });
      render(<CompaniesPage />);
      await userEvent.click(await screen.findByLabelText('Selecionar Barbearia Ficticia'));
      const bar = screen.getByRole('region', { name: 'Ações em lote' });
      await userEvent.type(within(bar).getByLabelText('Segmento para as selecionadas'), 'Barbearia');
      await userEvent.click(within(bar).getByRole('button', { name: 'Definir segmento' }));

      expect(await screen.findByRole('status')).toHaveTextContent('1 empresa atualizada.');
      expect(calls.find((call) => call.path === '/companies/bulk')?.body).toEqual({
        companyIds: [COMPANY_ID],
        segment: 'Barbearia',
      });
    });

    it('diferencia lista vazia de filtro sem resultado', async () => {
      listRoutes([]);

      render(<CompaniesPage />);

      expect(await screen.findByText('Nenhuma empresa cadastrada ainda.')).toBeInTheDocument();
      await userEvent.type(screen.getByLabelText('Cidade'), 'Outra');
      await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));
      expect(await screen.findByText('Nenhuma empresa corresponde aos filtros.')).toBeInTheDocument();
    });

    it('mostra erro com opcao de tentar de novo', async () => {
      let attempts = 0;
      listRoutes([], {
        'GET /companies/worklist': () => {
          attempts += 1;
          return attempts === 1 ? json({ type: 'UNEXPECTED_ERROR', status: 500 }, 500) : json(page([companyItem]));
        },
      });

      render(<CompaniesPage />);

      const alert = await screen.findByRole('alert');
      expect(alert).not.toHaveTextContent('500');
      await userEvent.click(within(alert).getByRole('button', { name: 'Tentar novamente' }));
      expect(await screen.findByRole('button', { name: 'Barbearia Ficticia' })).toBeInTheDocument();
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
        'POST /companies/duplicate-check': () => json([]),
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
      const calls = fakeApi({
        'POST /companies': () => json({ ...company, id: COMPANY_ID }, 201),
        'POST /companies/duplicate-check': () => json([]),
      });

      render(<CompanyNewPage />);
      await userEvent.type(screen.getByLabelText(/^Nome(?! comercial)/), 'Loja Ficticia');
      await userEvent.type(screen.getByLabelText(/^Cidade/), 'Cidade Exemplo');
      await userEvent.type(screen.getByLabelText(/^UF/), 'MG');
      await userEvent.click(screen.getByRole('button', { name: 'Cadastrar' }));

      await waitFor(() => {
        expect(window.location.pathname).toBe(`/companies/${COMPANY_ID}`);
      });
      const created = calls.find((call) => call.method === 'POST' && call.path === '/companies');
      expect(created?.body).toMatchObject({ name: 'Loja Ficticia', state: 'MG' });
      expect(created?.body).not.toHaveProperty('source');
    });

    it('avisa de possivel duplicata depois de digitar o telefone, sem impedir o cadastro', async () => {
      const calls = fakeApi({
        'POST /companies/duplicate-check': () =>
          json([
            {
              companyId: 'c-9',
              name: 'Padaria Existente',
              city: 'Cidade Exemplo',
              state: 'MG',
              archived: false,
              signals: ['PHONE'],
            },
          ]),
      });

      render(<CompanyNewPage />);
      await userEvent.type(screen.getByLabelText(/^Telefone/), '(35) 3333-4000');

      const link = await screen.findByRole('link', { name: 'Padaria Existente' }, { timeout: 2000 });
      expect(link).toHaveAttribute('href', '/companies/c-9');
      expect(link.closest('li')).toHaveTextContent('Padaria Existente · Cidade Exemplo/MG · mesmo telefone');
      // Uma consulta só, depois de parar de digitar.
      expect(calls.filter((call) => call.path === '/companies/duplicate-check')).toHaveLength(1);
      expect(screen.getByRole('button', { name: 'Cadastrar' })).toBeEnabled();
    });
  });

  describe('detalhe', () => {
    it('mostra resumo, dados, origem e duplicatas, e monta link seguro para o site', async () => {
      detailRoutes();

      render(<CompanyDetailPage id={COMPANY_ID} />);

      const site = await screen.findByRole('link', { name: 'barbearia.example.test' });
      expect(site).toHaveAttribute('href', 'https://barbearia.example.test/');
      expect(site).toHaveAttribute('rel', 'noopener noreferrer');
      expect(await screen.findByText('Mesmo domínio')).toBeInTheDocument();
      expect(await screen.findByText('Cadastro manual')).toBeInTheDocument();
      // Ausente é "não informado", não "não existe" (doc 06 §5).
      expect(screen.getAllByText('Não informado').length).toBeGreaterThan(0);
      const summary = await screen.findByLabelText('Resumo da empresa');
      expect(await within(summary).findByText('Sem lead')).toBeInTheDocument();
      expect(within(summary).getByText('Contato: telefone, site')).toBeInTheDocument();
      expect(await screen.findByText('Nenhum lead aberto para esta empresa ainda.')).toBeInTheDocument();
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

    it('exclui empresa limpa so depois de digitar o nome', async () => {
      const calls = detailRoutes({
        [`GET ${C}/removal`]: () => json({ allowed: true, allowedUntil: '2026-11-08T12:00:00Z' }),
        'DELETE /companies/': () => new Response(null, { status: 204 }),
      });

      render(<CompanyDetailPage id={COMPANY_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Excluir' }));
      const dialog = screen.getByRole('alertdialog', { name: 'Excluir empresa definitivamente?' });
      const confirm = within(dialog).getByRole('button', { name: 'Excluir' });
      expect(confirm).toBeDisabled();
      await userEvent.type(within(dialog).getByLabelText(/Digite/), 'barbearia ficticia');
      await userEvent.click(confirm);

      await waitFor(() => {
        expect(window.location.pathname).toBe('/companies');
      });
      expect(calls.find((call) => call.method === 'DELETE')?.body).toEqual({ confirmationName: 'barbearia ficticia' });
    });

    it('empresa com atividade explica por que nao exclui e oferece arquivar', async () => {
      const calls = detailRoutes({ 'POST /companies/': () => json({ ...company, archived: true }) });
      render(<CompanyDetailPage id={COMPANY_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Excluir' }));

      const dialog = screen.getByRole('alertdialog', { name: 'Esta empresa não pode ser excluída' });
      expect(dialog).toHaveTextContent('já tem histórico comercial');
      expect(within(dialog).queryByRole('button', { name: 'Excluir' })).not.toBeInTheDocument();
      await userEvent.click(within(dialog).getByRole('button', { name: 'Arquivar em vez disso' }));
      await waitFor(() => {
        expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/archive'))).toBe(true);
      });
      expect(calls.some((call) => call.method === 'DELETE')).toBe(false);
    });

    it('mescla a duplicata com confirmacao e avisa o que foi completado', async () => {
      const calls = detailRoutes({
        [`POST ${C}/merge`]: () => json({ company, filledFields: ['businessEmail'], movedLeads: 1 }),
      });

      render(<CompanyDetailPage id={COMPANY_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Mesclar Barbearia Ficticia Centro nesta' }));
      await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Mesclar' }));

      expect(await screen.findByText(/Completado: e-mail\./)).toBeInTheDocument();
      expect(calls.find((call) => call.path.endsWith('/merge'))?.body).toEqual({ sourceCompanyId: 'c-2' });
    });

    it('explica quando a empresa ja tem oportunidade aberta', async () => {
      detailRoutes({ 'POST /leads': () => json({ type: 'CONFLICT', status: 409 }, 409) });

      render(<CompanyDetailPage id={COMPANY_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Abrir lead' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('já tem uma oportunidade aberta');
    });
  });
});
