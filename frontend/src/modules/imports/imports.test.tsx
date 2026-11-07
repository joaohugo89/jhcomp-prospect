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
  mergedRows: 0,
  leadsCreated: 0,
  createdAt: '2026-11-05T12:00:00Z',
  ignoredColumns: ['Faturamento'],
};

const rows = [
  {
    rowNumber: 2,
    status: 'VALID',
    values: { name: 'Barbearia Um', city: 'Cidade Exemplo', state: 'MG' },
    errors: [],
    duplicateSignals: [],
  },
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
  {
    rowNumber: 7,
    status: 'DUPLICATE',
    values: { name: 'Barbearia Um', city: 'Cidade Exemplo', state: 'MG' },
    errors: [],
    duplicateOfRowNumber: 2,
    duplicateSignals: ['NAME_CITY_STATE'],
  },
];

function csvFile(content: string, name = 'empresas.csv'): File {
  return new File([content], name, { type: 'text/csv' });
}

function routes(overrides: Record<string, () => Response> = {}) {
  return fakeApi({
    'GET /imports/': () => json(batch),
    [`GET /imports/${IMPORT_ID}/report`]: () => json(page(rows)),
    [`POST /imports/${IMPORT_ID}/confirm`]: () =>
      json({ ...batch, status: 'COMPLETED', importedRows: 3, leadsCreated: 3 }),
    [`POST /imports/${IMPORT_ID}/discard`]: () => json({ ...batch, status: 'DISCARDED' }),
    ...overrides,
  });
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
            ? json(
                {
                  type: 'VALIDATION_ERROR',
                  status: 400,
                  detail: 'Required fields without a column: name, city, state',
                },
                400,
              )
            : json(batch, 201);
        },
      });

      render(<ImportsPage />);
      await userEvent.upload(screen.getByLabelText('Arquivo CSV'), csvFile('Cliente;Local;Sigla\nA;B;MG\n'));
      await userEvent.click(screen.getByRole('button', { name: 'Analisar arquivo' }));

      expect(await screen.findByRole('alert')).toHaveTextContent('Não encontramos as colunas obrigatórias');
      const submit = screen.getByRole('button', { name: 'Analisar arquivo' });
      // Without the three required columns it does not even upload.
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
        'POST /imports/csv': () =>
          json({ type: 'VALIDATION_ERROR', status: 400, detail: 'File must be UTF-8 encoded' }, 400),
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

  describe('historico', () => {
    it('mostra a visao geral, destaca o que aguarda revisao e filtra pela situacao', async () => {
      const calls = fakeApi({
        'GET /imports/summary': () =>
          json({ pendingReview: 1, completed: 3, undone: 1, discarded: 0, companiesImported: 12 }),
        'GET /imports': () =>
          json(
            page([
              batch,
              {
                ...batch,
                id: 'b-2',
                fileName: 'antiga.csv',
                status: 'COMPLETED',
                importedRows: 2,
                undoneAt: '2026-11-05T12:00:00Z',
              },
            ]),
          ),
      });

      render(<ImportsPage />);

      expect(await screen.findByText('12')).toBeInTheDocument();
      const table = await screen.findByRole('table', { name: 'Importações' });
      expect(within(table).getByText('Aguardando revisão')).toBeInTheDocument();
      expect(within(table).getByText('Desfeita')).toBeInTheDocument();
      expect(within(table).getByRole('link', { name: 'Revisar' })).toHaveAttribute('href', `/imports/${IMPORT_ID}`);

      await userEvent.click(screen.getByRole('button', { name: 'Ver: Aguardando revisão' }));
      await waitFor(() => {
        expect(
          calls
            .filter((call) => call.path === '/imports')
            .at(-1)
            ?.query.get('status'),
        ).toBe('ANALYZED');
      });
      expect(screen.getByRole('button', { name: 'Aguardando revisão', pressed: true })).toBeInTheDocument();
    });

    it('oferece o modelo CSV so com cabecalhos', async () => {
      fakeApi({ 'GET /imports': () => json(page([])) });
      const created: Blob[] = [];
      vi.stubGlobal('URL', {
        ...URL,
        createObjectURL: (blob: Blob) => {
          created.push(blob);
          return 'blob:modelo';
        },
        revokeObjectURL: () => undefined,
      });

      render(<ImportsPage />);
      await userEvent.click(await screen.findByRole('button', { name: 'Baixar modelo CSV' }));

      const text = await created[0]?.text();
      expect(text?.trim().split('\n')).toHaveLength(1);
      expect(text).toContain('nome;nome fantasia;segmento;cidade;uf');
    });
  });

  describe('revisao', () => {
    it('mostra totais, colunas ignoradas e o erro de cada linha em portugues', async () => {
      routes();

      render(<ImportDetailPage id={IMPORT_ID} />);

      expect(await screen.findByText('Faturamento')).toBeInTheDocument();
      const rejected = await screen.findByRole('article', { name: 'Linha 4' });
      expect(rejected).toHaveTextContent('Cidade: Campo obrigatório.');
      expect(
        within(screen.getByRole('article', { name: 'Linha 6' })).getByRole('link', { name: 'empresa já cadastrada' }),
      ).toHaveAttribute('href', `/companies/${COMPANY_ID}`);
    });

    it('duplicata de empresa cadastrada pode completar a existente; a do arquivo, so virar nova', async () => {
      const calls = routes();

      render(<ImportDetailPage id={IMPORT_ID} />);
      expect(await screen.findByRole('button', { name: 'Confirmar: 2 empresas novas' })).toBeEnabled();

      const ofExisting = await screen.findByRole('article', { name: 'Linha 6' });
      await userEvent.click(within(ofExisting).getByLabelText('Completar os campos vazios da empresa existente'));

      // A duplicate of another row in the file has no company to complete.
      const ofFile = screen.getByRole('article', { name: 'Linha 7' });
      expect(within(ofFile).queryByLabelText(/Completar/)).not.toBeInTheDocument();
      await userEvent.click(within(ofFile).getByLabelText(/Importar mesmo assim/));

      await userEvent.click(screen.getByLabelText(/Abrir um lead para cada empresa/));
      await userEvent.click(screen.getByRole('button', { name: 'Confirmar: 3 empresas novas e 1 completada' }));

      const dialog = screen.getByRole('alertdialog');
      expect(dialog).toHaveTextContent('sem oportunidade aberta');
      expect(calls.some((call) => call.path.endsWith('/confirm'))).toBe(false);

      await userEvent.click(within(dialog).getByRole('button', { name: 'Importar' }));

      await waitFor(() => {
        expect(calls.find((call) => call.path.endsWith('/confirm'))?.body).toEqual({
          includeDuplicateRows: [7],
          mergeDuplicateRows: [6],
          createLeads: true,
        });
      });
    });

    it('voltar para nao importar desfaz a escolha', async () => {
      routes();

      render(<ImportDetailPage id={IMPORT_ID} />);
      const ofExisting = await screen.findByRole('article', { name: 'Linha 6' });
      await userEvent.click(within(ofExisting).getByLabelText('Importar como empresa nova'));
      expect(screen.getByRole('button', { name: 'Confirmar: 3 empresas novas' })).toBeInTheDocument();

      await userEvent.click(within(ofExisting).getByLabelText('Não importar'));
      expect(screen.getByRole('button', { name: 'Confirmar: 2 empresas novas' })).toBeInTheDocument();
    });

    it('linha que completou empresa mostra os campos preenchidos', async () => {
      routes({
        'GET /imports/': () => json({ ...batch, status: 'COMPLETED', mergedRows: 1 }),
        [`GET /imports/${IMPORT_ID}/report`]: () =>
          json(
            page([
              {
                rowNumber: 6,
                status: 'MERGED',
                values: { name: 'Barbearia Um Filial', city: 'Outra', state: 'SP' },
                errors: [],
                duplicateOfCompanyId: COMPANY_ID,
                duplicateSignals: ['PHONE'],
                companyId: COMPANY_ID,
                mergedFields: ['segment', 'phone'],
              },
            ]),
          ),
      });

      render(<ImportDetailPage id={IMPORT_ID} />);

      const row = await screen.findByRole('article', { name: 'Linha 6' });
      expect(row).toHaveTextContent('Completou empresa existente');
      expect(row).toHaveTextContent('Campos preenchidos: Segmento, Telefone.');
      expect(screen.getByText('Completadas', { selector: 'dt' })).toBeInTheDocument();
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
      expect(screen.queryByRole('button', { name: /^Confirmar/ })).not.toBeInTheDocument();
      expect(screen.queryByLabelText(/Importar mesmo assim/)).not.toBeInTheDocument();
    });

    it('desfazer importacao mostra a previa do que sai e do que fica antes de confirmar', async () => {
      const completed = { ...batch, status: 'COMPLETED', importedRows: 3, completedAt: '2026-11-05T12:00:00Z' };
      let undone = false;
      const calls = routes({
        'GET /imports/': () => json(undone ? { ...completed, undoneAt: '2026-11-06T12:00:00Z' } : completed),
        [`GET /imports/${IMPORT_ID}/undo`]: () => json({ removed: 2, keptWithActivity: 1, mergedUntouched: 0 }),
        [`POST /imports/${IMPORT_ID}/undo`]: () => {
          undone = true;
          return json({ removed: 2, keptWithActivity: 1, mergedUntouched: 0, undoneAt: '2026-11-06T12:00:00Z' });
        },
      });

      render(<ImportDetailPage id={IMPORT_ID} />);
      await userEvent.click(await screen.findByRole('button', { name: 'Desfazer importação' }));

      const dialog = await screen.findByRole('alertdialog', { name: 'Desfazer esta importação?' });
      expect(dialog).toHaveTextContent('2 empresas criadas por ela, sem atividade, serão excluídas');
      expect(dialog).toHaveTextContent('1 empresa já tem atividade e fica');
      expect(calls.some((call) => call.method === 'POST')).toBe(false);
      await userEvent.click(within(dialog).getByRole('button', { name: 'Desfazer importação' }));

      expect(await screen.findByText(/Importação desfeita em/)).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: 'Desfazer importação' })).not.toBeInTheDocument();
    });
  });
});
