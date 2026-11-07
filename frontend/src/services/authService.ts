import type { Session } from '../types/session';
import { request } from './httpClient';

/**
 * Session authentication (decision D1).
 *
 * No credential is ever held in JavaScript: the session lives in the HttpOnly
 * cookie the backend issues, and the browser sends it back on its own.
 */
export function login(email: string, password: string): Promise<Session> {
  return request<Session>('/auth/login', { method: 'POST', body: { email, password } });
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' });
}

/** The current session. Throws HttpError 401 when there is none. */
export function fetchSession(signal?: AbortSignal): Promise<Session> {
  return request<Session>('/auth/me', signal ? { signal } : {});
}

/** Changes your own password. Ends the user's other sessions; this one survives (D28). */
export function changePassword(currentPassword: string, newPassword: string): Promise<Session> {
  return request<Session>('/auth/password', { method: 'POST', body: { currentPassword, newPassword } });
}
