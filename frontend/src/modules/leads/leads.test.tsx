import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, lead, outreachContext, page } from '../../test/fakeApi';
import { LeadDetailPage } from './LeadDetailPage';

function routes(statusResponse: () => Response = () => json({ ...lead, status: 'CONTACTED' })) {
  return fakeApi({
    'GET /leads/': () => json(lead),
    'GET /leads/22222222-2222-4222-8222-222222222222/analyses': () => json(page([])),
    'GET /leads/22222222-2222-4222-8222-222222222222/tasks': () => json(page([])),
    'GET /leads/22222222-2222-4222-8222-222222222222/outreach-context': () => json(outreachContext),
    'GET /leads/22222222-2222-4222-8222-222222222222/messages': () => json(page([])),
    'GET /leads/22222222-2222-4222-8222-222222222222/deal': () =>
      json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404),
    'GET /leads/22222222-2222-4222-8222-222222222222/timeline': () =>
      json(
        page([
          {
            type: 'STATUS_CHANGE',
            id: 'e-1',
            occurredAt: '2026-11-01T12:00:00Z',
            actorName: 'Operadora',
            toStatus: 'NEW',
          },
          {
            type: 'SOURCE',
            id: 'e-2',
            occurredAt: '2026-11-01T12:00:00Z',
            sourceType: 'MANUAL',
            sourceName: 'Indicacao',
          },
        ]),
      ),
    'PATCH /leads/': statusResponse,
  });
}

async function selectStatus(label: string): Promise<void> {
  await userEvent.selectOptions(await screen.findByLabelText('Novo status'), label);
}

/** Mudança de status na tela (doc 06 §2). */
describe('Detalhe do lead', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('so oferece as transicoes permitidas pelo servidor', async () => {
    routes();

    render(<LeadDetailPage id={LEAD_ID} />);

    const select = await screen.findByLabelText('Novo status');
    const options = within(select)
      .getAllByRole('option')
      .map((option) => option.textContent);
    expect(options).toEqual(['Selecione…', 'Qualificado', 'Contatado', 'Perdido', 'Não contatar']);
    expect(await screen.findByText('Lead aberto como Novo')).toBeInTheDocument();
    expect(screen.getByText(/Origem: Indicacao/)).toBeInTheDocument();
  });

  it('CONTACTED exige confirmar que o contato ocorreu', async () => {
    const calls = routes();

    render(<LeadDetailPage id={LEAD_ID} />);
    await selectStatus('Contatado');

    const submit = screen.getByRole('button', { name: 'Alterar status' });
    expect(submit).toBeDisabled();

    await userEvent.click(screen.getByLabelText(/contato com a empresa foi de fato realizado/));
    expect(submit).toBeEnabled();
    await userEvent.click(submit);

    await waitFor(() => {
      expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
        status: 'CONTACTED',
        contactConfirmed: true,
      });
    });
    expect(await screen.findAllByText('Contatado')).not.toHaveLength(0);
  });

  it('trocar de status desmarca a confirmacao de contato', async () => {
    routes();

    render(<LeadDetailPage id={LEAD_ID} />);
    await selectStatus('Contatado');
    await userEvent.click(screen.getByLabelText(/contato com a empresa foi de fato realizado/));
    await selectStatus('Qualificado');
    await selectStatus('Contatado');

    expect(screen.getByLabelText(/contato com a empresa foi de fato realizado/)).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Alterar status' })).toBeDisabled();
  });

  it('NAO CONTATAR pede confirmacao e nao envia ao cancelar', async () => {
    const calls = routes(() => json({ ...lead, status: 'DO_NOT_CONTACT', allowedTransitions: [] }));

    render(<LeadDetailPage id={LEAD_ID} />);
    await selectStatus('Não contatar');
    await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }));

    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveTextContent('não pode ser desfeito');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    expect(calls.some((call) => call.method === 'PATCH')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }));
    await userEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText(/estado final/)).toBeInTheDocument();
  });

  it('transicao recusada pelo servidor mostra aviso util', async () => {
    routes(() => json({ type: 'CONFLICT', status: 409, detail: 'Status transition not allowed' }, 409));

    render(<LeadDetailPage id={LEAD_ID} />);
    await selectStatus('Qualificado');
    await userEvent.click(screen.getByRole('button', { name: 'Alterar status' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('não é permitida no estado atual');
    expect(alert).not.toHaveTextContent('Status transition');
  });
});
