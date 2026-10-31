import { describe, expect, it } from 'vitest';

import { safeHref } from './format';
import { parseRoute } from './router';

describe('safeHref', () => {
  it.each([
    ['https://loja.example.test/a', 'https://loja.example.test/a'],
    ['http://loja.example.test', 'http://loja.example.test/'],
    ['loja.example.test', 'https://loja.example.test/'],
    ['loja.example.test:8080/inicio', 'https://loja.example.test:8080/inicio'],
  ])('aceita %s', (url, expected) => {
    expect(safeHref(url)).toBe(expected);
  });

  /** Defesa em profundidade: mesmo que um dado ruim chegue da API, não vira script. */
  it.each([
    'javascript:alert(1)',
    'JavaScript:alert(1)',
    'javascript:1,alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
    'ftp://arquivos.example.test',
    '',
    '   ',
  ])('recusa %s', (url) => {
    expect(safeHref(url)).toBeUndefined();
  });
});

describe('parseRoute', () => {
  const id = '11111111-1111-4111-8111-111111111111';

  it.each([
    ['/', { name: 'dashboard' }],
    ['/dashboard', { name: 'dashboard' }],
    ['/pipeline/', { name: 'pipeline' }],
    ['/leads', { name: 'leads' }],
    [`/leads/${id}`, { name: 'lead', id }],
    ['/companies/', { name: 'companies' }],
    ['/companies/new', { name: 'companyNew' }],
    [`/companies/${id}`, { name: 'company', id }],
    ['/settings/members', { name: 'members' }],
    ['/companies/nao-e-uuid', { name: 'notFound' }],
    ['/admin', { name: 'notFound' }],
  ])('%s', (path, expected) => {
    expect(parseRoute(path)).toEqual(expected);
  });
});
