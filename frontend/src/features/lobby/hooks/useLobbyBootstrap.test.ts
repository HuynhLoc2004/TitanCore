import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  request: vi.fn(),
  cached: vi.fn(() => null),
}));
const auth = vi.hoisted(() => {
  const listeners = new Set<() => void>();
  return {
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    invalidate: () => listeners.forEach((listener) => listener()),
    listenerCount: () => listeners.size,
  };
});

vi.mock('../api/lobbyApi', () => ({
  LobbyApiError: class LobbyApiError extends Error {
    constructor(readonly failure: string) {
      super(failure);
    }
  },
  requestLobbyBootstrap: api.request,
  getCachedLobby: api.cached,
}));
vi.mock('../../../auth/api', () => ({
  subscribeAuthGeneration: auth.subscribe,
}));

import { LobbyApiError } from '../api/lobbyApi';
import { useLobbyBootstrap } from './useLobbyBootstrap';

function content(overrides: Record<string, unknown> = {}) {
  return {
    schemaVersion: 1 as const,
    generatedAt: '2026-07-27T12:00:00Z',
    locale: 'vi-VN' as const,
    player: { displayName: 'Hero', profileVersion: 1 },
    navigation: [],
    sections: [],
    nextContentBoundaryAt: null,
    degraded: { active: false, codes: [] },
    ...overrides,
  };
}

function loaded(overrides: Record<string, unknown> = {}) {
  return {
    content: content(overrides),
    clientDegradedCodes: [],
    etag: '"etag"',
    contentLanguage: 'vi-VN' as const,
    cacheControl: 'private, no-cache',
    revalidated: false,
  };
}

afterEach(() => {
  vi.useRealTimers();
  api.request.mockReset();
  api.cached.mockReset();
  api.cached.mockReturnValue(null);
});

describe('useLobbyBootstrap', () => {
  it('transitions from bootstrapping to an authenticated empty shell', async () => {
    api.request.mockResolvedValue(loaded());
    const { result } = renderHook(() => useLobbyBootstrap('user-one', 'vi-VN'));

    expect(result.current.state.status).toBe('BOOTSTRAPPING');
    await waitFor(() => expect(result.current.state.status).toBe('EMPTY'));
    expect(api.request).toHaveBeenCalledTimes(1);
  });

  it('retries availability failures at most twice and then becomes offline', async () => {
    vi.useFakeTimers();
    api.request.mockRejectedValue(new LobbyApiError('AVAILABILITY'));
    const { result } = renderHook(() => useLobbyBootstrap('user-one', 'vi-VN'));

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    expect(api.request).toHaveBeenCalledTimes(3);
    expect(result.current.state.status).toBe('OFFLINE');
  });

  it('does not retry authentication or content failures', async () => {
    api.request.mockRejectedValue(new LobbyApiError('AUTH_REQUIRED'));
    const { result } = renderHook(() => useLobbyBootstrap('user-one', 'vi-VN'));

    await waitFor(() => expect(result.current.state.status).toBe('AUTH_REQUIRED'));
    expect(api.request).toHaveBeenCalledTimes(1);
  });

  it('aborts and clears content synchronously when auth generation changes', async () => {
    let signal: AbortSignal | undefined;
    api.request.mockImplementation(({ signal: requestSignal }) => {
      signal = requestSignal;
      return new Promise(() => undefined);
    });
    const { result } = renderHook(() => useLobbyBootstrap('user-one', 'vi-VN'));

    act(() => auth.invalidate());

    expect(signal?.aborted).toBe(true);
    expect(result.current.state).toMatchObject({ status: 'AUTH_REQUIRED', content: null });
  });

  it('refreshes once at a content boundary and cleans listeners on unmount', async () => {
    vi.useFakeTimers();
    api.request.mockResolvedValue(loaded({
      nextContentBoundaryAt: new Date(Date.now() + 100).toISOString(),
    }));
    const { result, unmount } = renderHook(
      () => useLobbyBootstrap('user-one', 'vi-VN'),
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(result.current.state.status).toBe('EMPTY');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(api.request).toHaveBeenCalledTimes(2);

    unmount();
    expect(auth.listenerCount()).toBe(0);
  });

  it('revalidates after online and visible events without a request loop', async () => {
    api.request.mockResolvedValue(loaded());
    const { unmount } = renderHook(() => useLobbyBootstrap('user-one', 'vi-VN'));
    await waitFor(() => expect(api.request).toHaveBeenCalledTimes(1));

    act(() => window.dispatchEvent(new Event('online')));
    await waitFor(() => expect(api.request).toHaveBeenCalledTimes(2));

    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      value: 'visible',
    });
    act(() => document.dispatchEvent(new Event('visibilitychange')));
    await waitFor(() => expect(api.request).toHaveBeenCalledTimes(3));

    unmount();
    act(() => {
      window.dispatchEvent(new Event('online'));
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(api.request).toHaveBeenCalledTimes(3);
  });
});
