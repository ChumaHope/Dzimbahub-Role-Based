import { API, Role } from './api';

export type AuthUser = { id: number; fullName: string; email: string; role: Role; authenticatorEnabled?: boolean };
export type AuthResult = { token: string; user: AuthUser };
/**
 * method TOTP: type the code from the authenticator app.
 * method TOTP_SETUP: first sign-in; scan otpauthUri (or type secret) into the app, then type its code.
 * method CODE: a code was sent (channel says how).
 */
export type Challenge = {
  challengeId: string; method: 'TOTP' | 'TOTP_SETUP' | 'CODE'; phoneHint?: string; channel?: 'WHATSAPP' | 'SMS';
  devCode?: string; otpauthUri?: string; secret?: string; account?: string; allowFallback?: boolean;
};

const TOKEN_KEY = 'dzimbahub_token';
const USER_KEY = 'dzimbahub_user';

export function saveAuth(result: AuthResult) {
  if (typeof window === 'undefined') return;
  localStorage.setItem(TOKEN_KEY, result.token);
  localStorage.setItem(USER_KEY, JSON.stringify(result.user));
  window.dispatchEvent(new Event('dzimbahub-auth-change'));
}
export function getToken() { return typeof window === 'undefined' ? null : localStorage.getItem(TOKEN_KEY); }
export function getUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  try { const raw = localStorage.getItem(USER_KEY); return raw ? (JSON.parse(raw) as AuthUser) : null; } catch { return null; }
}
export function clearAuth() {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY);
  window.dispatchEvent(new Event('dzimbahub-auth-change'));
}

async function authPost<T>(path: string, body: object): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API}/auth${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  } catch {
    throw new Error('Cannot reach the server. Check your internet connection and try again.');
  }
  const data = await response.json().catch(() => ({ message: 'The server returned an unreadable response.' }));
  if (!response.ok) throw new Error(data.message || `Request failed (${response.status})`);
  return data as T;
}

/** Step 1 of sign-up: creates a request that an administrator must approve. No token is returned. */
export const signupRequest = (body: { fullName: string; email: string; phone: string; password: string; role: 'TENANT' | 'LANDLORD' }) =>
  authPost<{ status: string; message: string }>('/signup', body);
/** Step 1 of sign-in: checks e-mail and password and sends a code by WhatsApp (or SMS). */
export const loginRequest = (body: { email: string; password: string }) => authPost<Challenge>('/login', body);
/** Step 2 of sign-in: checks the code and returns the session token. */
export const verifyCode = (body: { challengeId: string; code: string }) => authPost<AuthResult>('/verify-otp', body);
/** "Get a code another way": sends a code instead of using the authenticator app. */
export const sendFallbackCode = (challengeId: string) => authPost<Challenge>('/send-code', { challengeId });
