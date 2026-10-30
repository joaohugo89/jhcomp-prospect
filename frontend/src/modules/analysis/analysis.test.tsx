import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { LEAD_ID, fakeApi, json, lead, page } from '../../test/fakeApi';
import type { Lead } from '../../types/lead';
import { AnalysisSection } from './AnalysisSection';

const ANALYSIS_ID = '55555555-5555-4555-8555-555555555555';

const pending = { id: ANALYSIS_ID, leadId: LEAD_ID, status: 'PENDING', createdAt: '2026-11-05T12:00:00Z' };
const completed = {
  ...pending,
  status: 'COMPLETED',
  score: 35,
  scoreVersion: 'v1',
  completedAt: '2026-11-05T12:00:05Z',
};

const finding = (criterion: string, status: string, points: number, max: number, extra = {}) => ({
  id: `f-${criterion}`,
  criterion,
  status,
  originalStatus: status,
  description: `Descrição de ${criterion}`,
  pointsAwarded: points,
  maxPoints: max,
  reviewStatus: 'PENDING',
  evidence: [],
  ...extra,
});

const detail = {
  analysis: completed,
  findings: [
    finding('SITE_ABSENT', 'NOT_VERIFIED', 0, 25),
    finding('NO_HTTPS', 'CONFIRMED', 10, 10, {
      confidence: 'HIGH',
      evidence: [
        {
          type: 'HTTP_RESPONSE',
          referenceUrl: 'http://loja.example.test/',
          description: 'HTTPS sem resposta; HTTP respondeu.',
          observedAt: '2026-11-05T12:00:03Z',
          capturedBy: 'WEBSITE_CHECK/v1',
        },
      ],
    }),
    finding('LOCATION_SERVED', 'CONFIRMED', 10, 10),
  ],
};

const sampleLead = lead as unknown as Lead;

describe('Presença digital', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('sem analise, explica que nao ha score e permite pedir', async () => {
    let requested = false;
    const calls = fakeApi({
      'GET /leads/': () => json(page(requested ? [pending] : [])),
      'POST /leads/': () => {
        requested = true;
        return json(pending, 202);
      },
    });

    render(<AnalysisSection lead={sampleLead} onScoreChanged={() => undefined} />);

    expect(await screen.findByText(/Sem análise, o lead não tem score/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Analisar' }));

    expect(await screen.findByText(/Na fila desde/)).toBeInTheDocument();
    expect(calls.some((call) => call.method === 'POST' && call.path.endsWith('/analyses'))).toBe(true);
    // Em andamento, nao da para pedir outra.
    expect(screen.getByRole('button', { name: 'Analisar de novo' })).toBeDisabled();
  });

  it('acompanha a analise em andamento e avisa quando o score muda', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    let done = false;
    fakeApi({
      'GET /leads/': () => json(page([done ? completed : pending])),
      [`GET /analyses/${ANALYSIS_ID}`]: () => json(detail),
    });
    const onScoreChanged = vi.fn<() => void>();

    render(<AnalysisSection lead={sampleLead} onScoreChanged={onScoreChanged} />);
    expect(await screen.findByText(/Na fila desde/)).toBeInTheDocument();

    done = true;
    await vi.advanceTimersByTimeAsync(3000);

    expect(await screen.findByText('35')).toBeInTheDocument();
    await waitFor(() => {
      expect(onScoreChanged).toHaveBeenCalledTimes(1);
    });
  });

  it('mostra decomposicao, estado em texto, evidencia e prioridade sugerida', async () => {
    fakeApi({
      'GET /leads/': () => json(page([completed])),
      [`GET /analyses/${ANALYSIS_ID}`]: () => json(detail),
    });

    render(<AnalysisSection lead={sampleLead} onScoreChanged={() => undefined} />);

    expect(await screen.findByText('35')).toBeInTheDocument();
    // Nao verificado nunca aparece como fato confirmado (RF-031).
    expect(screen.getByText('Não verificado')).toBeInTheDocument();
    expect(screen.getByText('0/25')).toBeInTheDocument();
    expect(screen.getByText(/Prioridade sugerida pelas faixas do score: Média/)).toBeInTheDocument();
    expect(screen.getByText(/não probabilidade de compra/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'http://loja.example.test/' })).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('revisao envia o estado e a nota, e atualiza o score', async () => {
    const calls = fakeApi({
      'GET /leads/': () => json(page([completed])),
      [`GET /analyses/${ANALYSIS_ID}`]: () => json(detail),
      'PATCH /findings/': () =>
        json({
          analysis: { ...completed, score: 60 },
          findings: detail.findings.map((item) =>
            item.criterion === 'SITE_ABSENT'
              ? { ...item, status: 'CONFIRMED', pointsAwarded: 25, reviewStatus: 'CORRECTED', reviewedAt: '2026-11-05T13:00:00Z' }
              : item,
          ),
        }),
    });
    const onScoreChanged = vi.fn<() => void>();

    render(<AnalysisSection lead={sampleLead} onScoreChanged={onScoreChanged} />);
    const siteAbsent = (await screen.findByText('Empresa sem site')).closest('li');
    if (siteAbsent === null) {
      throw new Error('achado nao encontrado');
    }
    await userEvent.click(within(siteAbsent).getByRole('button', { name: 'Revisar achado' }));
    await userEvent.selectOptions(within(siteAbsent).getByLabelText('Estado após a revisão'), 'Confirmado');
    await userEvent.type(within(siteAbsent).getByLabelText('Nota (vira evidência)'), 'Confirmado por telefone.');
    await userEvent.click(within(siteAbsent).getByRole('button', { name: 'Corrigir estado' }));

    expect(await screen.findByText('60')).toBeInTheDocument();
    expect(calls.find((call) => call.method === 'PATCH')?.body).toEqual({
      status: 'CONFIRMED',
      note: 'Confirmado por telefone.',
    });
    expect(onScoreChanged).toHaveBeenCalledTimes(1);
  });

  it('analise com falha explica e permite tentar de novo', async () => {
    fakeApi({
      'GET /leads/': () => json(page([{ ...pending, status: 'FAILED', failureReason: 'A verificação não pôde ser concluída.' }])),
    });

    render(<AnalysisSection lead={sampleLead} onScoreChanged={() => undefined} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('falhou');
    expect(screen.getByRole('button', { name: 'Analisar de novo' })).toBeEnabled();
  });

  it('lead marcado como nao contatar nao pode ser analisado', async () => {
    fakeApi({ 'GET /leads/': () => json(page([])) });

    render(<AnalysisSection lead={{ ...sampleLead, status: 'DO_NOT_CONTACT' }} onScoreChanged={() => undefined} />);

    expect(await screen.findByText(/não contatar: não é analisado/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Analisar' })).toBeDisabled();
  });
});
