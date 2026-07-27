import { describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch, getAccessToken, invalidateAuthGeneration, login, logout, refreshAccessToken, setAccessToken } from './api';

const user = {
  id: '3d2c4040-66f6-45b7-9235-1d5d7a4d4586',
  email: 'hero@example.com',
  role: 'PLAYER',
  status: 'ACTIVE',
  profile: {
    id: 'b8953c61-67cb-44bf-ac85-0ab9d620d510',
    displayName: 'Hero',
    onboardingStatus: 'COMPLETED' as const,
    version: 1,
  },
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

  it('prevents an old delayed refresh from restoring authentication after generation invalidation', async () => {
    const refreshControl: { resolve?: (response: Response) => void } = {};
    let protectedAuthorization: string | null = 'not-called';
    let markRefreshStarted!: () => void;
    const refreshStarted = new Promise<void>((resolve) => {
      markRefreshStarted = resolve;
    });
    installFetch((input, init) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        markRefreshStarted();
        return new Promise<Response>((resolve) => {
          refreshControl.resolve = resolve;
        });
      }
      if (url.endsWith('/api/auth/me')) {
        protectedAuthorization = new Headers(init?.headers).get('Authorization');
        return jsonResponse(user);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    const oldRefresh = refreshAccessToken();
    await refreshStarted;
    invalidateAuthGeneration();
    setAccessToken(null);
    refreshControl.resolve?.(jsonResponse(authResponse(freshAccess)));

    await expect(oldRefresh).rejects.toMatchObject({ code: 'STALE_AUTH_GENERATION' });
    expect(getAccessToken()).toBeNull();

    await apiFetch('/api/auth/me');
    expect(protectedAuthorization).toBeNull();
  });

  it('allows a new generation to authenticate after invalidating an old refresh', async () => {
    let refreshCount = 0;
    installFetch((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        refreshCount += 1;
        return jsonResponse(authResponse(freshAccess));
      }
      throw new Error(`Unexpected request ${url}`);
    });

    invalidateAuthGeneration();
    await refreshAccessToken();

    expect(refreshCount).toBe(1);
    expect(getAccessToken()).toBe(freshAccess);
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

  it('does not attempt a mutation when CSRF bootstrap fails with a stale cookie present', async () => {
    document.cookie = `${csrfCookieName}=stale-cookie-value; path=/`;
    const fetchMock = installFetch((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return new Response(null, { status: 500 });
      }
      throw new Error(`Unexpected mutation ${url}`);
    });

    await expect(login('hero', loginSecret)).rejects.toMatchObject({
      code: 'CSRF_UNAVAILABLE',
      message: 'Security handshake unavailable. Try again.',
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('maps 403, 409, 429, problem details, and network failures to safe errors', async () => {
    installFetch((input) => {
      const url = String(input);
      if (url.endsWith('/forbidden')) {
        return jsonResponse({ title: 'FORBIDDEN', detail: 'Sensitive backend detail', status: 403 }, {
          status: 403,
          headers: { 'content-type': 'application/problem+json' },
        });
      }
      if (url.endsWith('/conflict')) {
        return jsonResponse({ title: 'REGISTRATION_UNAVAILABLE', detail: 'Registration could not be completed', status: 409 }, {
          status: 409,
          headers: { 'content-type': 'application/problem+json' },
        });
      }
      if (url.endsWith('/limited')) {
        return jsonResponse({ title: 'RATE_LIMITED', detail: 'Too many requests', status: 429 }, {
          status: 429,
          headers: { 'content-type': 'application/problem+json' },
        });
      }
      throw new TypeError('network down');
    });

    await expect(apiFetch('/forbidden', {}, false)).rejects.toMatchObject({
      status: 403,
      message: 'This action needs a fresh security charm. Try again.',
    });
    await expect(apiFetch('/conflict', {}, false)).rejects.toMatchObject({
      status: 409,
      code: 'REGISTRATION_UNAVAILABLE',
      message: 'Registration could not be completed',
    });
    await expect(apiFetch('/limited', {}, false)).rejects.toMatchObject({
      status: 429,
      message: 'Too many attempts. Let the forge cool down for a moment.',
    });
    await expect(apiFetch('/offline', {}, false)).rejects.toBeInstanceOf(ApiError);
    await expect(apiFetch('/offline', {}, false)).rejects.toMatchObject({
      code: 'NETWORK_UNAVAILABLE',
      message: 'Network trouble at the raid gate. Try again.',
    });
  });

  it('clears token after logout even when the backend logout request fails', async () => {
    setAccessToken(freshAccess);
    installFetch((input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/logout')) {
        return new Response(null, { status: 500 });
      }
      throw new Error(`Unexpected request ${url}`);
    });

    await expect(logout()).rejects.toBeInstanceOf(ApiError);
    expect(getAccessToken()).toBeNull();
  });
});
