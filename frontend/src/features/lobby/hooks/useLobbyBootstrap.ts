import { useCallback, useEffect, useRef, useState } from 'react';
import { subscribeAuthGeneration } from '../../../auth/api';
import {
  getCachedLobby,
  LobbyApiError,
  requestLobbyBootstrap,
} from '../api/lobbyApi';
import {
  initialLobbyState,
  resolvedLobbyState,
  transientLobbyState,
  type LobbyState,
} from '../model/lobbyState';
import type { SupportedLobbyLocale } from '../model/lobbyTypes';

const MAX_RETRIES = 2;
const MIN_BOUNDARY_DELAY_MS = 1_000;
const MAX_BOUNDARY_DELAY_MS = 30 * 60 * 1_000;

export function useLobbyBootstrap(subjectId: string, locale: SupportedLobbyLocale) {
  const [state, setState] = useState<LobbyState>(initialLobbyState);
  const stateRef = useRef(state);
  const requestSequence = useRef(0);
  const activeController = useRef<AbortController | null>(null);
  const firedBoundary = useRef<string | null>(null);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  const load = useCallback(async (initial: boolean) => {
    activeController.current?.abort();
    const controller = new AbortController();
    activeController.current = controller;
    const sequence = ++requestSequence.current;
    const previous = stateRef.current.content
      ?? getCachedLobby(subjectId, locale)?.content
      ?? null;
    setState(initial
      ? { ...initialLobbyState }
      : transientLobbyState('RETRYING', previous));

    for (let attempt = 0; attempt <= MAX_RETRIES; attempt += 1) {
      try {
        const loaded = await requestLobbyBootstrap({
          subjectId,
          locale,
          signal: controller.signal,
        });
        if (sequence !== requestSequence.current || controller.signal.aborted) {
          return;
        }
        setState(resolvedLobbyState(loaded.content, loaded.clientDegradedCodes));
        return;
      } catch (error) {
        if (sequence !== requestSequence.current || controller.signal.aborted) {
          return;
        }
        const failure = error instanceof LobbyApiError ? error.failure : 'FATAL';
        if (failure === 'ABORTED') {
          return;
        }
        if (failure === 'AUTH_REQUIRED' || failure === 'ONBOARDING_REQUIRED') {
          setState({ status: failure, content: null, degradedCodes: [] });
          return;
        }
        if (failure !== 'AVAILABILITY') {
          setState({ status: 'FATAL_ERROR', content: null, degradedCodes: [] });
          return;
        }
        if (attempt < MAX_RETRIES && navigator.onLine !== false) {
          setState(transientLobbyState('RETRYING', previous));
          try {
            await abortableDelay(retryDelay(attempt), controller.signal);
          } catch {
            return;
          }
          continue;
        }
        setState(transientLobbyState('OFFLINE', previous));
        return;
      }
    }
  }, [locale, subjectId]);

  useEffect(() => {
    void load(true);
    const clearForAuthChange = () => {
      requestSequence.current += 1;
      activeController.current?.abort();
      setState({ status: 'AUTH_REQUIRED', content: null, degradedCodes: [] });
    };
    const unsubscribe = subscribeAuthGeneration(clearForAuthChange);
    const online = () => void load(false);
    const visible = () => {
      if (document.visibilityState === 'visible') {
        void load(false);
      }
    };
    window.addEventListener('online', online);
    document.addEventListener('visibilitychange', visible);
    return () => {
      requestSequence.current += 1;
      activeController.current?.abort();
      unsubscribe();
      window.removeEventListener('online', online);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [load]);

  useEffect(() => {
    const boundary = state.content?.nextContentBoundaryAt;
    if (!boundary || boundary === firedBoundary.current) {
      return;
    }
    const parsed = Date.parse(boundary);
    if (!Number.isFinite(parsed)) {
      return;
    }
    const delay = Math.min(
      MAX_BOUNDARY_DELAY_MS,
      Math.max(MIN_BOUNDARY_DELAY_MS, parsed - Date.now()),
    );
    const timer = window.setTimeout(() => {
      firedBoundary.current = boundary;
      void load(false);
    }, delay);
    return () => window.clearTimeout(timer);
  }, [load, state.content?.nextContentBoundaryAt]);

  const retry = useCallback(() => {
    void load(false);
  }, [load]);

  return { state, retry };
}

function retryDelay(attempt: number) {
  const base = attempt === 0 ? 250 : 600;
  return base + Math.floor(Math.random() * 100);
}

function abortableDelay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const finish = () => {
      signal.removeEventListener('abort', abort);
      resolve();
    };
    const timer = window.setTimeout(finish, milliseconds);
    const abort = () => {
      window.clearTimeout(timer);
      signal.removeEventListener('abort', abort);
      reject(new DOMException('Aborted', 'AbortError'));
    };
    signal.addEventListener('abort', abort, { once: true });
    if (signal.aborted) {
      abort();
    }
  });
}
