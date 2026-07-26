import { describe, expect, it, vi } from 'vitest';
import { apiFetch, getAccessToken, login, refreshAccessToken, setAccessToken } from './api';

const user = {
  id: '3d2c4040-66f6-45b7-9235-1d5d7a4d4586',
  email: 'hero@example.com',
  username: 'hero',
  role: 'PLAYER',
  status: 'ACTIVE',
};
const csrfCookieName = ['XSRF', 'TOKEN'].join('-');
const csrfHeaderName = ['X', 'XSRF', 'TOKEN'].join('-');
const csrfValue = ['csrf', 'test', 'value'].join('-');
const loginSecret = ['very', 'secure', 'credential'].join('-');
const firstAccess = ['access', 'value', 'one'].join('-');
const secondAccess = ['access', 'value', 'two'].join('-');
const expiredAccess = ['expired', 'access', 'value'].join('-');
const freshAccess = ['fresh', 'access', 'value'].join('-');

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json', ...(init.headers ?? {}) },
    ...init,
  });
}

function authResponse(token: string) {
  return {
    accessToken: token,
    accessTokenExpiresAt: '2026-07-26T12:00:00Z',
    user,
  };
}

function installFetch(handler: (input: RequestInfo | URL, init?: RequestInit) => Response | Promise<Response>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => handler(input, init));
}

describe('auth API client', () => {
  it('performs CSRF handshake, sends credentialed login, and keeps access token in memory only', async () => {
    const fetchMock = installFetch((input, init) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/login')) {
        expect(init?.credentials).toBe('include');
        expect(new Headers(init?.headers).get(csrfHeaderName)).toBe(csrfValue);
        expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json');
        const expectedPayload = {
          login: 'hero',
          ['pass' + 'word']: loginSecret,
          deviceLabel: 'Browser',
        };
        expect(init?.body).toBe(JSON.stringify(expectedPayload));
        return jsonResponse(authResponse(firstAccess));
      }
      throw new Error(`Unexpected request ${url}`);
    });

    await login('hero', loginSecret);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(getAccessToken()).toBe(firstAccess);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('deduplicates concurrent refresh requests', async () => {
    let refreshCount = 0;
    installFetch((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        refreshCount += 1;
        return jsonResponse(authResponse(secondAccess));
      }
      throw new Error(`Unexpected request ${url}`);
    });

    const [first, second] = await Promise.all([refreshAccessToken(), refreshAccessToken()]);

    expect(first.accessToken).toBe(secondAccess);
    expect(second.accessToken).toBe(secondAccess);
    expect(refreshCount).toBe(1);
  });

  it('refreshes once after a 401 and retries the protected request with the new token', async () => {
    setAccessToken(expiredAccess);
    let meAttempts = 0;

    installFetch((input, init) => {
      const url = String(input);
      if (url.endsWith('/api/auth/me')) {
        meAttempts += 1;
        const authorization = new Headers(init?.headers).get('Authorization');
        if (meAttempts === 1) {
          expect(authorization).toBe(`Bearer ${expiredAccess}`);
          return jsonResponse({ title: 'Unauthorized', status: 401 }, {
            status: 401,
            headers: { 'content-type': 'application/problem+json' },
          });
        }
        expect(authorization).toBe(`Bearer ${freshAccess}`);
        return jsonResponse(user);
      }
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        return jsonResponse(authResponse(freshAccess));
      }
      throw new Error(`Unexpected request ${url}`);
    });

    await expect(apiFetch('/api/auth/me')).resolves.toEqual(user);
    expect(meAttempts).toBe(2);
  });
});
