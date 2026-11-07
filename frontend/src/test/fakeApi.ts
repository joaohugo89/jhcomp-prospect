import { vi } from 'vitest';

/**
 * A fake API for interface tests. No frontend test depends on the network or
 * on the backend.
 *
 * Routes are `"METHOD /path"`; the path matches by prefix, ignoring the query
 * string, and the longest route wins. Every call is recorded so a test can
 * check what was sent.
 */
export interface RecordedCall {
  readonly method: string;
  readonly path: string;
  readonly query: URLSearchParams;
  readonly body: unknown;
  /** The multipart body, when the call uploads a file. */
  readonly form: FormData | undefined;
}

type Handler = (call: RecordedCall) => Response;

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function page<T>(
  content: readonly T[],
  overrides: Partial<{ page: number; totalPages: number; hasNext: boolean }> = {},
) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    hasNext: false,
    ...overrides,
  };
}

export function fakeApi(routes: Readonly<Record<string, Handler>>): RecordedCall[] {
  const calls: RecordedCall[] = [];
  const ordered = Object.entries(routes).toSorted(([a], [b]) => b.length - a.length);

  vi.stubGlobal(
    'fetch',
    vi.fn((url: string, init?: RequestInit) => {
      const parsed = new URL(url, 'http://localhost');
      const path = parsed.pathname.replace(/^\/api\/v1/, '');
      const method = init?.method ?? 'GET';
      const body: unknown = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined;
      const form = init?.body instanceof FormData ? init.body : undefined;
      const call: RecordedCall = { method, path, query: parsed.searchParams, body, form };
      calls.push(call);

      const handler = ordered.find(([route]) => {
        const [routeMethod, routePath] = route.split(' ');
        return routeMethod === method && routePath !== undefined && path.startsWith(routePath);
      })?.[1];

      return Promise.resolve(handler ? handler(call) : json({ type: 'RESOURCE_NOT_FOUND', status: 404 }, 404));
    }),
  );

  return calls;
}

export const session = {
  userId: 'u-1',
  email: 'admin@jhcomp.local',
  displayName: 'Administrador',
  organizationId: 'org-1',
  role: 'ADMIN' as const,
  passwordChangeRequired: false,
};

export const COMPANY_ID = '11111111-1111-4111-8111-111111111111';
export const LEAD_ID = '22222222-2222-4222-8222-222222222222';

export const company = {
  id: COMPANY_ID,
  name: 'Barbearia Ficticia',
  segment: 'BARBERSHOP',
  city: 'Cidade Exemplo',
  state: 'MG',
  websiteUrl: 'barbearia.example.test',
  archived: false,
  createdAt: '2026-11-01T12:00:00Z',
  updatedAt: '2026-11-02T12:00:00Z',
};

export const lead = {
  id: LEAD_ID,
  company: {
    id: COMPANY_ID,
    name: 'Barbearia Ficticia',
    segment: 'BARBERSHOP',
    city: 'Cidade Exemplo',
    state: 'MG',
    archived: false,
  },
  status: 'CONTACT_APPROVED' as const,
  priority: 'MEDIUM' as const,
  createdAt: '2026-11-01T12:00:00Z',
  updatedAt: '2026-11-02T12:00:00Z',
  allowedTransitions: ['QUALIFIED', 'CONTACTED', 'LOST', 'DO_NOT_CONTACT'] as const,
};

/** A minimal compose context, for screens that show the lead without testing outreach. */
export const outreachContext = {
  senderOrganization: 'JH.COMP',
  senderName: 'Administrador',
  leadStatus: 'CONTACT_APPROVED',
  companyName: 'Barbearia Ficticia',
  city: 'Cidade Exemplo',
  state: 'MG',
  confirmedFindings: [],
  channels: [],
};

/** The work queue row (`/leads/worklist`) for the same fictitious lead. */
export const worklistItem = {
  leadId: LEAD_ID,
  companyId: COMPANY_ID,
  companyName: 'Barbearia Ficticia',
  segment: 'BARBERSHOP',
  city: 'Cidade Exemplo',
  state: 'MG',
  status: 'CONTACT_APPROVED' as const,
  priority: 'MEDIUM' as const,
  createdAt: '2026-11-01T12:00:00Z',
  updatedAt: '2026-11-02T12:00:00Z',
};

/**
 * The routes the lead list calls besides the queue: views, segments and team.
 * Declared together because the fake API matches by prefix, and `GET /leads`
 * alone would answer all of them with the same JSON.
 */
export function leadListRoutes(items: readonly unknown[]): Readonly<Record<string, Handler>> {
  return {
    'GET /leads/worklist': () => json(page(items)),
    'GET /leads/views': () => json([]),
    'GET /companies/segments': () => json({ segments: [], withoutSegment: 0 }),
    'GET /team': () => json(page([])),
  };
}
