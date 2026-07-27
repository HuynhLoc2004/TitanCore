import { afterEach, describe, expect, it, vi } from 'vitest';

const auth = vi.hoisted(() => {
  let generation = 0;
  const listeners = new Set<(value: number) => void>();
  return {
    response: vi.fn(),
    getGeneration: () => generation,
    subscribe: (listener: (value: number) => void) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    advance: () => {
      generation += 1;
      listeners.forEach((listener) => listener(generation));
    },
  };
});
vi.mock('../../../auth/api', () => ({
  ApiError: class ApiError extends Error {
    code = 'NETWORK_UNAVAILABLE';
  },
  authenticatedFetchResponse: auth.response,
  getAuthGeneration: auth.getGeneration,
  subscribeAuthGeneration: auth.subscribe,
}));

import {
  clearLobbyMemory,
  getCachedLobby,
  requestLobbyBootstrap,
  resolveLobbyLocale,
} from './lobbyApi';

const subjectId = '3d2c4040-66f6-45b7-9235-1d5d7a4d4586';

function payload(locale = 'vi-VN') {
  return {
    schemaVersion: 1,
    generatedAt: '2026-07-27T12:00:00Z',
    locale,
    player: { displayName: 'Hiệp Sĩ', profileVersion: 4 },
    navigation: [],
    sections: [],
    nextContentBoundaryAt: null,
    degraded: { active: false, codes: [] },
  };
}

function ok(locale = 'vi-VN', etag = '"etag-one"') {
  return new Response(JSON.stringify(payload(locale)), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Content-Language': locale,
      'Cache-Control': 'private, no-cache, must-revalidate',
      ETag: etag,
    },
  });
}

afterEach(() => {
  clearLobbyMemory();
  auth.response.mockReset();
  window.localStorage.clear();
  window.sessionStorage.clear();
});

describe('lobby bootstrap API', () => {
  it('loads a typed 200 response and sends only the selected locale', async () => {
    auth.response.mockResolvedValue(ok());

    const result = await requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });
    const init = auth.response.mock.calls[0][1] as RequestInit;

    expect(result.content.locale).toBe('vi-VN');
    expect(result.etag).toBe('"etag-one"');
    expect(new Headers(init.headers).get('Accept-Language')).toBe('vi-VN');
    expect(new Headers(init.headers).has('Authorization')).toBe(false);
    expect(window.localStorage).toHaveLength(0);
    expect(window.sessionStorage).toHaveLength(0);
  });

  it('uses a bodyless 304 only with the exact matching in-memory body', async () => {
    auth.response.mockResolvedValueOnce(ok()).mockResolvedValueOnce(new Response(null, {
      status: 304,
      headers: { ETag: '"etag-one"', 'Content-Language': 'vi-VN' },
    }));

    const first = await requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });
    const second = await requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });
    const init = auth.response.mock.calls[1][1] as RequestInit;

    expect(second.content).toEqual(first.content);
    expect(second.revalidated).toBe(true);
    expect(new Headers(init.headers).get('If-None-Match')).toBe('"etag-one"');
  });

  it('performs one unconditional refetch for a 304 without a cached body', async () => {
    auth.response.mockResolvedValueOnce(new Response(null, { status: 304 }))
      .mockResolvedValueOnce(ok());

    await expect(requestLobbyBootstrap({ subjectId, locale: 'vi-VN' }))
      .resolves.toMatchObject({ revalidated: false });
    expect(auth.response).toHaveBeenCalledTimes(2);
    const secondInit = auth.response.mock.calls[1][1] as RequestInit;
    expect(new Headers(secondInit.headers).has('If-None-Match')).toBe(false);
  });

  it('separates locale cache entries and replaces their ETags independently', async () => {
    auth.response.mockResolvedValueOnce(ok('vi-VN', '"vi-one"'))
      .mockResolvedValueOnce(ok('en-US', '"en-one"'))
      .mockResolvedValueOnce(ok('vi-VN', '"vi-two"'));

    await requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });
    await requestLobbyBootstrap({ subjectId, locale: 'en-US' });
    await requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });

    const english = new Headers((auth.response.mock.calls[1][1] as RequestInit).headers);
    const vietnamese = new Headers((auth.response.mock.calls[2][1] as RequestInit).headers);
    expect(english.has('If-None-Match')).toBe(false);
    expect(vietnamese.get('If-None-Match')).toBe('"vi-one"');
    expect(getCachedLobby(subjectId, 'en-US')?.content.locale).toBe('en-US');
  });

  it('deduplicates one in-flight request per generation, subject and locale', async () => {
    let resolve!: (response: Response) => void;
    auth.response.mockReturnValue(new Promise<Response>((done) => {
      resolve = done;
    }));

    const first = requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });
    const second = requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });
    resolve(ok());

    await expect(Promise.all([first, second])).resolves.toHaveLength(2);
    expect(auth.response).toHaveBeenCalledTimes(1);
  });

  it('clears cache and rejects a stale response after an auth-generation change', async () => {
    let resolve!: (response: Response) => void;
    auth.response.mockReturnValue(new Promise<Response>((done) => {
      resolve = done;
    }));
    const pending = requestLobbyBootstrap({ subjectId, locale: 'vi-VN' });

    auth.advance();
    resolve(ok());

    await expect(pending).rejects.toMatchObject({ failure: 'ABORTED' });
    expect(getCachedLobby(subjectId, 'vi-VN')).toBeNull();
  });

  it('selects only a supported client locale', () => {
    expect(resolveLobbyLocale(['fr-FR', 'en-GB'])).toBe('en-US');
    expect(resolveLobbyLocale(['VI-vn'])).toBe('vi-VN');
    expect(resolveLobbyLocale(['ja-JP'])).toBe('vi-VN');
  });
});
