import type { Session } from '../types/session';
import { request } from './httpClient';

/**
 * Autenticacao por sessao (decisao D1).
 *
 * Nenhuma credencial e guardada em JavaScript: a sessao vive no cookie
 * HttpOnly que o backend emite, e o navegador a reenvia sozinho.
 */
export function login(email: string, password: string): Promise<Session> {
  return request<Session>('/auth/login', { method: 'POST', body: { email, password } });
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' });
}

/** Sessao corrente. Lanca HttpError 401 quando nao houver. */
export function fetchSession(signal?: AbortSignal): Promise<Session> {
  return request<Session>('/auth/me', signal ? { signal } : {});
}

/** Troca da própria senha. Encerra as outras sessões do usuário; esta continua (D28). */
export function changePassword(currentPassword: string, newPassword: string): Promise<Session> {
  return request<Session>('/auth/password', { method: 'POST', body: { currentPassword, newPassword } });
}
