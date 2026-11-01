import { describe, expect, it } from 'vitest';

import type { BoardCard } from '../../types/crm';
import { daysLabel, signalsOf } from './pipelineSignals';

/** Regras dos sinais do Kanban, com o "agora" fixo. Dados fictícios. */
const NOW = Date.parse('2026-11-06T12:00:00Z');
const DAY = 86_400_000;

const base: BoardCard = {
  leadId: '22222222-2222-4222-8222-222222222222',
  companyName: 'Loja Ficticia',
  city: 'Cidade Exemplo',
  state: 'MG',
  status: 'QUALIFIED',
  priority: 'MEDIUM',
  stageEnteredAt: new Date(NOW - 3 * DAY).toISOString(),
  allowedTransitions: ['CONTACT_APPROVED'],
};

describe('signalsOf', () => {
  it('conta dias inteiros na etapa e marca parado a partir de 7', () => {
    expect(signalsOf(base, NOW)).toEqual({ daysInStage: 3, stale: false, overdueTask: false });
    const parado = signalsOf({ ...base, stageEnteredAt: new Date(NOW - 7 * DAY).toISOString() }, NOW);
    expect(parado.stale).toBe(true);
  });

  it('tarefa com prazo vencido e atrasada; no futuro, nao', () => {
    expect(signalsOf({ ...base, nextTaskDueAt: new Date(NOW - 60_000).toISOString() }, NOW).overdueTask).toBe(true);
    expect(signalsOf({ ...base, nextTaskDueAt: new Date(NOW + DAY).toISOString() }, NOW).overdueTask).toBe(false);
  });

  it('sem interacao so a partir de 14 dias, e so quando ha alguma registrada', () => {
    expect(
      signalsOf({ ...base, lastInteractionAt: new Date(NOW - 13 * DAY).toISOString() }, NOW).quietDays,
    ).toBeUndefined();
    expect(signalsOf({ ...base, lastInteractionAt: new Date(NOW - 14 * DAY).toISOString() }, NOW).quietDays).toBe(14);
    expect(signalsOf(base, NOW).quietDays).toBeUndefined();
  });

  it('etapa encerrada nao gera alerta: o tempo nela nao pede acao', () => {
    const ganho = signalsOf(
      {
        ...base,
        status: 'WON',
        stageEnteredAt: new Date(NOW - 40 * DAY).toISOString(),
        nextTaskDueAt: new Date(NOW - DAY).toISOString(),
        lastInteractionAt: new Date(NOW - 40 * DAY).toISOString(),
      },
      NOW,
    );
    expect(ganho).toEqual({ daysInStage: 40, stale: false, overdueTask: false });
  });

  it('data no futuro (relogio adiantado) nao vira dia negativo', () => {
    expect(signalsOf({ ...base, stageEnteredAt: new Date(NOW + DAY).toISOString() }, NOW).daysInStage).toBe(0);
  });
});

describe('daysLabel', () => {
  it('hoje, singular e plural', () => {
    expect(daysLabel(0)).toBe('hoje');
    expect(daysLabel(1)).toBe('1 dia');
    expect(daysLabel(12)).toBe('12 dias');
  });
});
