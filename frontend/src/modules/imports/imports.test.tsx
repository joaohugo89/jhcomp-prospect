import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { COMPANY_ID, fakeApi, json, page } from '../../test/fakeApi';
import { parseHeaderLine } from './csvHeaders';
import { ImportDetailPage } from './ImportDetailPage';
import { ImportsPage } from './ImportsPage';

const IMPORT_ID = '33333333-3333-4333-8333-333333333333';

const batch = {
  id: IMPORT_ID,
  fileName: 'empresas.csv',
  sourceName: 'Planilha de outubro',
  status: 'ANALYZED',
  columnMapping: { name: 'Razão Social', city: 'Município', state: 'UF' },
  totalRows: 5,
  validRows: 2,
  rejectedRows: 2,
  duplicateRows: 1,
  importedRows: 0,
  leadsCreated: 0,
  createdAt: '2026-11-05T12:00:00Z',
  ignoredColumns: ['Faturamento'],
};

const rows = [
  { rowNumber: 2, status: 'VALID', values: { name: 'Barbearia Um', city: 'Cidade Exemplo', state: 'MG' }, errors: [], duplicateSignals: [] },
  {
    rowNumber: 4,
    status: 'REJECTED',
    values: { name: 'Sem Cidade', state: 'MG' },
    errors: [{ field: 'city', message: 'must not be blank' }],
    duplicateSignals: [],
  },
  {
    rowNumber: 6,
    status: 'DUPLICATE',
    values: { name: 'Barbearia Um Filial', city: 'Outra', state: 'SP' },
    errors: [],
    duplicateOfCompanyId: COMPANY_ID,
    duplicateSignals: ['PHONE'],
  },
];

function csvFile(content: string, name = 'empresas.csv'): File {
  return new File([content], name, { type: 'text/csv' });
}

describe('parseHeaderLine', () => {
  it('le ponto e virgula, BOM e aspas como o backend', () => {
    expect(parseHeaderLine('﻿Razão Social;"Cidade; UF";Site\r\nA;B;C')).toEqual(['Razão Social', 'Cidade; UF', 'Site']);
    expect(parseHeaderLine('nome,cidade\n')).toEqual(['nome', 'cidade']);
    expect(parseHeaderLine('"Loja ""X""",uf')).toEqual(['Loja "X"', 'uf']);
  });
});

describe('Importações', () => {
  beforeEach(() => {
    window.history.replaceState(null, '', '/imports');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('envio', () => {
    it('pede o mapeamento quando o backend nao reconhece as colunas obrigatorias', async () => {
      let attempt = 0;
      const calls = fakeApi({
        'GET /imports': () => json(page([])),
        'POST /imports/csv': () => {
          attempt += 1;
          return attempt === 1
            ? json({ type: 'VALIDATION_ERROR', status: 400, detail: 'Required fields without a column: name, city, state' }, 400)
            : json(batch, 201);
        },
      });

      render(<ImportsPage />);
      await userEvent.upload(screen.getByLabelText('Arquivo CSV'), csvFile('Cliente;Local;Sigla\nA;B;MG\n'));
      await userEvent.click(screen.getByRole('button', { name: 'Analisar arquivo' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Não encontramos as colunas obrigatórias');
      const submit = screen.getByRole('button', { name: 'Analisar arquivo' });
      // Sem os três obrigatórios, nem envia.
      expect(submit).toBeDisabled();

      await userEvent.selectOptions(screen.getByLabelText(/^Nome\b(?! comercial)/), 'Cliente');
      await userEvent.selectOptions(screen.getByLabelText(/^Cidade/), 'Local');
      await userEvent.selectOptions(screen.getByLabelText(/^UF/), 'Sigla');
      await userEvent.click(submit);

      await waitFor(() => {
        expect(window.location.pathname).toBe(`/imports/${IMPORT_ID}`);
      });
      const sent = calls.filter((call) => call.method === 'POST')[1]?.form;
      expect(JSON.parse(String(sent?.get('mapping')))).toEqual({ name: 'Cliente', city: 'Local', state: 'Sigla' });
      expect(sent?.get('file')).toBeInstanceOf(File);
    });

    it('explica como corrigir arquivo fora de UTF-8', async () => {
      fakeApi({
        'GET /imports': () => json(page([])),
        'POST /imports/csv': () => json({ type: 'VALIDATION_ERROR', status: 400, detail: 'File must be UTF-8 encoded' }, 400),
      });

      render(<ImportsPage />);
      await userEvent.upload(screen.getByLabelText('Arquivo CSV'), csvFile('nome;cidade;uf\n'));
      await userEvent.click(screen.getByRole('button', { name: 'Analisar arquivo' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent('CSV UTF-8');
      expect(alert).not.toHaveTextContent('encoded');
    });

    it('so envia origem e mapeamento quando informados', async () => {
      const calls = fakeApi({
        'GET /imports': () => json(page([])),
        'POST /imports/csv': () => json(batch, 201),
      });

      render(<ImportsPage />);
      await userEvent.upload(screen.getByLabelText('Arquivo CSV'), csvFile('nome;cidade;uf\nA;B;MG\n'));
      await userEvent.click(screen.getByRole('button', { name: 'Analisar arquivo' }));

      await waitFor(() => {
        expect(calls.some((call) => call.method === 'POST')).toBe(true);
      });
      const form = calls.find((call) => call.method === 'POST')?.form;
      expect(form?.has('sourceName')).toBe(false);
      expect(form?.has('mapping')).toBe(false);
    });
  });

  describe('revisao', () => {
    function routes(overrides: Record<string, () => Response> = {}) {
      return fakeApi({
        'GET /imports/': () => json(batch),
        [`GET /imports/${IMPORT_ID}/report`]: () => json(page(rows)),
        [`POST /imports/${IMPORT_ID}/confirm`]: () => json({ ...batch, status: 'COMPLETED', importedRows: 3, leadsCreated: 3 }),
        [`POST /imports/${IMPORT_ID}/discard`]: () => json({ ...batch, status: 'DISCARDED' }),
        ...overrides,
      });
    }

    it('mostra totais, colunas ignoradas e o erro de cada linha em portugues', async () => {
      routes();

      render(<ImportDetailPage id={IMPORT_ID} />);

      expect(await screen.findByText('Faturamento')).toBeInTheDocument();
      const rejected = await screen.findByRole('article', { name: 'Linha 4' });
      expect(rejected).toHaveTextContent('Cidade: Campo obrigatório.');
      expect(within(screen.getByRole('article', { name: 'Linha 6' })).getByRole('link', { name: 'empresa já cadastrada' }))
        .toHaveAttribute('href', `/companies/${COMPANY_ID}`);
    });

    it('importa as validas e as duplicatas marcadas, depois de confirmar', async () => {
      const calls = routes();

      render(<ImportDetailPage id={IMPORT_ID} />);
      expect(await screen.findByRole('button', { name: 'Importar 2 empresas' })).toBeEnabled();

      const duplicate = await screen.findByRole('article', { name: 'Linha 6' });
      await userEvent.click(within(duplicate).getByLabelText(/Importar mesmo assim/));
      await userEvent.click(screen.getByLabelText('Abrir um lead para cada empresa importada'));
      await userEvent.click(screen.getByRole('button', { name: 'Importar 3 empresas' }));

      const dialog = screen.getByRole('alertdialog');
      expect(dialog).toHaveTextContent('cada uma com um lead novo');
      expect(calls.some((call) => call.path.endsWith('/confirm'))).toBe(false);

      await userEvent.click(within(dialog).getByRole('button', { name: 'Importar' }));

      await waitFor(() => {
        expect(calls.find((call) => call.path.endsWith('/confirm'))?.body).toEqual({
          includeDuplicateRows: [6],
          createLeads: true,
        });
      });
    });

    it('descartar pede confirmacao', async () => {
      const calls = routes();

      render(<ImportDetailPage id={IMPORT_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Descartar' }));

      const dialog = screen.getByRole('alertdialog');
      expect(dialog).toHaveTextContent('dados das linhas serão apagados');
      await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
      expect(calls.some((call) => call.path.endsWith('/discard'))).toBe(false);
    });

    it('avisa reenvio do mesmo arquivo', async () => {
      routes({ 'GET /imports/': () => json({ ...batch, previousImportId: '44444444-4444-4444-8444-444444444444' }) });

      render(<ImportDetailPage id={IMPORT_ID} />);

      expect(await screen.findByText(/já foi enviado antes/)).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Ver importação anterior' })).toHaveAttribute(
        'href',
        '/imports/44444444-4444-4444-8444-444444444444',
      );
    });

    it('importacao concluida nao oferece revisao', async () => {
      routes({ 'GET /imports/': () => json({ ...batch, status: 'COMPLETED', importedRows: 2 }) });

      render(<ImportDetailPage id={IMPORT_ID} />);

      expect(await screen.findByText('Importadas', { selector: 'dt' })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /^Importar/ })).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/Importar mesmo assim/)).not.toBeInTheDocument();
    });
  });
});
