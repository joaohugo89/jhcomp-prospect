import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, lead, outreachContext, page, type RecordedCall } from '../../test/fakeApi';
import { LeadDetailPage } from './LeadDetailPage';
import { LeadsPage } from './LeadsPage';

/** Drill-down do dashboard (filtros na URL) e responsável pelo lead. Dados fictícios. */
describe('Lista de leads com filtros na URL', () => {
  beforeEach(() => {
    window.history.replaceState(
      null,
      '',
      '/leads?status=PROPOSAL_SENT&source=CSV_IMPORT&city=Cidade+Exemplo&priority=XYZ',
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.replaceState(null, '', '/');
  });

  it('abre ja filtrada pela URL e ignora valor desconhecido', async () => {
    const calls = fakeApi({ 'GET /leads': () => json(page([lead])) });

    render(<LeadsPage />);

    await screen.findByRole('link', { name: 'Barbearia Ficticia' });
    const query = calls.find((call) => call.path === '/leads')?.query;
    expect(query?.get('status')).toBe('PROPOSAL_SENT');
    expect(query?.get('source')).toBe('CSV_IMPORT');
    expect(query?.get('city')).toBe('Cidade Exemplo');
    expect(query?.get('priority')).toBeNull();
    expect(screen.getByLabelText('Status')).toHaveValue('PROPOSAL_SENT');
    expect(screen.getByLabelText('Origem')).toHaveValue('CSV_IMPORT');
  });

  it('filtrar grava a busca na URL para compartilhar e voltar', async () => {
    fakeApi({ 'GET /leads': () => json(page([lead])) });

    render(<LeadsPage />);
    await screen.findByRole('link', { name: 'Barbearia Ficticia' });
    fireEvent.change(screen.getByLabelText('Segmento'), { target: { value: 'Barbearia' } });
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }));

    expect(new URLSearchParams(window.location.search).get('segment')).toBe('Barbearia');
    expect(new URLSearchParams(window.location.search).get('status')).toBe('PROPOSAL_SENT');
  });
});

/** Rotas do detalhe: todas as seções precisam da sua, porque a API simulada casa por prefixo. */
function detailRoutes(patchResponse: (call: RecordedCall) => Response) {
  return fakeApi({
    'GET /leads/': () => json(lead),
    [`GET /leads/${LEAD_ID}/analyses`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/tasks`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/outreach-context`]: () => json(outreachContext),
    [`GET /leads/${LEAD_ID}/messages`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/timeline`]: () => json(page([])),
    [`GET /leads/${LEAD_ID}/deal`]: () => json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404),
    'GET /team': () => json(page([{ userId: 'u-2', displayName: 'Operadora Ficticia' }])),
    'PATCH /leads/': patchResponse,
  });
}

describe('Responsavel pelo lead', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('atribui um membro da equipe e mostra quem e', async () => {
    const calls = detailRoutes(() => json({ ...lead, assignedUserId: 'u-2' }));
    render(<LeadDetailPage id={LEAD_ID} />);

    const select = await screen.findByLabelText('Responsável pelo lead');
    await waitFor(() => {
      expect(within(select).getByRole('option', { name: 'Operadora Ficticia' })).toBeInTheDocument();
    });
    expect(select).toHaveValue('');
    await userEvent.selectOptions(select, 'Operadora Ficticia');

    await waitFor(() => {
      expect(select).toHaveValue('u-2');
    });
    const patch = calls.find((call) => call.method === 'PATCH');
    expect(patch?.path).toBe(`/leads/${LEAD_ID}`);
    expect(patch?.body).toEqual({ assignedUserId: 'u-2' });
  });

  it('membro que deixou de estar ativo recebe aviso claro', async () => {
    detailRoutes(() => json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404));
    render(<LeadDetailPage id={LEAD_ID} />);

    const select = await screen.findByLabelText('Responsável pelo lead');
    await waitFor(() => {
      expect(within(select).getByRole('option', { name: 'Operadora Ficticia' })).toBeInTheDocument();
    });
    await userEvent.selectOptions(select, 'Operadora Ficticia');

    expect(await screen.findByText(/não é mais membro ativo/)).toBeInTheDocument();
  });
});
