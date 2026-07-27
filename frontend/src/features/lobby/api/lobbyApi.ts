import {
  ApiError,
  authenticatedFetchResponse,
  getAuthGeneration,
  subscribeAuthGeneration,
} from '../../../auth/api';
import type { ParsedLobbyBootstrap, SupportedLobbyLocale } from '../model/lobbyTypes';
import {
  InvalidLobbyBootstrapError,
  parseLobbyBootstrap,
} from '../registry/lobbyContentRegistry';

type CacheEntry = {
  etag: string | null;
  parsed: ParsedLobbyBootstrap;
};

export type LobbyLoadResult = ParsedLobbyBootstrap & {
  etag: string | null;
  contentLanguage: SupportedLobbyLocale;
  cacheControl: string;
  revalidated: boolean;
};

export type LobbyApiFailure =
  | 'ABORTED'
  | 'AUTH_REQUIRED'
  | 'ONBOARDING_REQUIRED'
  | 'AVAILABILITY'
  | 'FATAL';

export class LobbyApiError extends Error {
  constructor(readonly failure: LobbyApiFailure) {
    super(failure);
  }
}
const cache = new Map<string, CacheEntry>();
const inFlight = new Map<string, { controller: AbortController; promise: Promise<LobbyLoadResult> }>();

subscribeAuthGeneration(() => clearLobbyMemory());

export function resolveLobbyLocale(languages: readonly string[] = navigator.languages) {
  for (const language of languages) {
    const normalized = language.toLowerCase();
    if (normalized === 'vi' || normalized.startsWith('vi-')) {
      return 'vi-VN' as const;
    }
    if (normalized === 'en' || normalized.startsWith('en-')) {
      return 'en-US' as const;
    }
  }
  return 'vi-VN' as const;
}

export function getCachedLobby(
  subjectId: string,
  locale: SupportedLobbyLocale,
): ParsedLobbyBootstrap | null {
  return cache.get(cacheKey(getAuthGeneration(), subjectId, locale))?.parsed ?? null;
}

export function clearLobbyMemory() {
  inFlight.forEach(({ controller }) => controller.abort());
  inFlight.clear();
  cache.clear();
}

export function requestLobbyBootstrap(options: {
  subjectId: string;
  locale: SupportedLobbyLocale;
  signal?: AbortSignal;
}): Promise<LobbyLoadResult> {
  const generation = getAuthGeneration();
  const key = cacheKey(generation, options.subjectId, options.locale);
  const existing = inFlight.get(key);
  if (existing) {
    return existing.promise;
  }

  const controller = new AbortController();
  const abort = () => controller.abort();
  options.signal?.addEventListener('abort', abort, { once: true });
  if (options.signal?.aborted) {
    controller.abort();
  }

  const promise = performRequest({
    ...options,
    generation,
    key,
    signal: controller.signal,
    unconditional: false,
  }).finally(() => {
    options.signal?.removeEventListener('abort', abort);
    if (inFlight.get(key)?.promise === promise) {
      inFlight.delete(key);
    }
  });
  inFlight.set(key, { controller, promise });
  return promise;
}

async function performRequest(options: {
  subjectId: string;
  locale: SupportedLobbyLocale;
  generation: number;
  key: string;
  signal: AbortSignal;
  unconditional: boolean;
}): Promise<LobbyLoadResult> {
  const cached = cache.get(options.key);
  const headers = new Headers({ 'Accept-Language': options.locale });
  if (!options.unconditional && cached?.etag) {
    headers.set('If-None-Match', cached.etag);
  }

  let response: Response;
  try {
    response = await authenticatedFetchResponse('/api/lobby/bootstrap', {
      method: 'GET',
      headers,
      signal: options.signal,
    });
  } catch (error) {
    if (isAbort(error)) {
      throw new LobbyApiError('ABORTED');
    }
    if (error instanceof ApiError && error.code === 'NETWORK_UNAVAILABLE') {
      throw new LobbyApiError('AVAILABILITY');
    }
    throw new LobbyApiError('FATAL');
  }

  assertCurrent(options);
  if (response.status === 304) {
    if (cached) {
      return result(cached, options.locale, response, true);
    }
    if (!options.unconditional) {
      return performRequest({ ...options, unconditional: true });
    }
    throw new LobbyApiError('FATAL');
  }
  if (response.status === 401) {
    throw new LobbyApiError('AUTH_REQUIRED');
  }
  if (response.status === 403) {
    const code = await safeProblemCode(response);
    throw new LobbyApiError(
      code === 'PROFILE_ONBOARDING_REQUIRED' ? 'ONBOARDING_REQUIRED' : 'AUTH_REQUIRED',
    );
  }
  if (response.status === 503) {
    throw new LobbyApiError('AVAILABILITY');
  }
  if (!response.ok) {
    throw new LobbyApiError('FATAL');
  }

  let parsed: ParsedLobbyBootstrap;
  try {
    parsed = parseLobbyBootstrap(await response.json());
  } catch (error) {
    if (error instanceof InvalidLobbyBootstrapError) {
      throw new LobbyApiError('FATAL');
    }
    throw new LobbyApiError('FATAL');
  }
  if (parsed.content.locale !== options.locale) {
    throw new LobbyApiError('FATAL');
  }
  assertCurrent(options);

  const cacheControl = response.headers.get('Cache-Control') ?? '';
  const entry: CacheEntry = {
    etag: response.headers.get('ETag'),
    parsed,
  };
  if (!cacheControl.toLowerCase().includes('no-store')) {
    cache.set(options.key, entry);
  }
  return result(entry, options.locale, response, false);
}

function result(
  entry: CacheEntry,
  requestedLocale: SupportedLobbyLocale,
  response: Response,
  revalidated: boolean,
): LobbyLoadResult {
  const responseLocale = response.headers.get('Content-Language');
  const contentLanguage = responseLocale === 'en-US' || responseLocale === 'vi-VN'
    ? responseLocale
    : requestedLocale;
  return {
    ...entry.parsed,
    etag: entry.etag,
    contentLanguage,
    cacheControl: response.headers.get('Cache-Control') ?? '',
    revalidated,
  };
}

function assertCurrent(options: { generation: number; signal: AbortSignal }) {
  if (options.signal.aborted) {
    throw new LobbyApiError('ABORTED');
  }
  if (options.generation !== getAuthGeneration()) {
    throw new LobbyApiError('ABORTED');
  }
}

async function safeProblemCode(response: Response) {
  try {
    const body = await response.json() as { title?: unknown };
    return typeof body.title === 'string' ? body.title : '';
  } catch {
    return '';
  }
}

function cacheKey(generation: number, subjectId: string, locale: SupportedLobbyLocale) {
  return `${generation}:${subjectId}:${locale}`;
}

function isAbort(error: unknown) {
  return error instanceof DOMException && error.name === 'AbortError';
}
