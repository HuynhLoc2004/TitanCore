export type User = {
  id: string;
  email: string;
  username: string;
  role: string;
  status: string;
};

export type AuthResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  user: User;
};

export type ProblemDetails = {
  title?: string;
  detail?: string;
  status?: number;
  path?: string;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
let accessToken: string | null = null;
let refreshPromise: Promise<AuthResponse> | null = null;

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export async function fetchCsrfToken() {
  await fetch(`${apiBaseUrl}/api/auth/csrf`, {
    credentials: 'include',
    headers: { Accept: 'application/problem+json, application/json' },
  });
  const csrfCookie = readCookie('XSRF-TOKEN');
  if (!csrfCookie) {
    throw new ApiError(503, 'CSRF_UNAVAILABLE', 'Security handshake unavailable. Try again.');
  }
  return csrfCookie;
}

export async function login(loginValue: string, password: string, deviceLabel = 'Browser') {
  const response = await authMutation<AuthResponse>('/api/auth/login', {
    login: loginValue,
    password,
    deviceLabel,
  });
  setAccessToken(response.accessToken);
  return response;
}

export async function register(email: string, username: string, password: string, deviceLabel = 'Browser') {
  const response = await authMutation<AuthResponse>('/api/auth/register', {
    email,
    username,
    password,
    deviceLabel,
  });
  setAccessToken(response.accessToken);
  return response;
}

export async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = authMutation<AuthResponse>('/api/auth/refresh', undefined)
      .then((response) => {
        setAccessToken(response.accessToken);
        return response;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function fetchMe() {
  return apiFetch<User>('/api/auth/me', { method: 'GET' });
}

export async function logout() {
  await authMutation<void>('/api/auth/logout', undefined);
  setAccessToken(null);
}

async function authMutation<T>(path: string, body: unknown) {
  const csrf = await fetchCsrfToken();
  return apiFetch<T>(path, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': csrf },
    body: body === undefined ? undefined : JSON.stringify(body),
  }, false);
}

export async function apiFetch<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/problem+json, application/json');
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  });

  if (response.status === 401 && allowRefresh) {
    try {
      await refreshAccessToken();
      return apiFetch<T>(path, init, false);
    } catch {
      setAccessToken(null);
    }
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

async function toApiError(response: Response) {
  const fallback = new ApiError(response.status, 'REQUEST_FAILED', 'The raid gate is grumpy. Try again.');
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/problem+json')) {
    return fallback;
  }
  try {
    const problem = (await response.json()) as ProblemDetails;
    return new ApiError(
      response.status,
      problem.title ?? 'REQUEST_FAILED',
      safeMessage(problem),
    );
  } catch {
    return fallback;
  }
}

function safeMessage(problem: ProblemDetails) {
  if (problem.status === 429) {
    return 'Too many attempts. Let the forge cool down for a moment.';
  }
  if (problem.status === 401) {
    return 'That combo did not open the raid gate.';
  }
  if (problem.status === 403) {
    return 'This action needs a fresh security charm. Try again.';
  }
  if (problem.status === 400) {
    return problem.detail ?? 'Check the marked fields and try again.';
  }
  return problem.detail ?? 'Something went sideways. Try again soon.';
}

function readCookie(name: string) {
  const encodedName = `${encodeURIComponent(name)}=`;
  return document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(encodedName))
    ?.slice(encodedName.length) ?? null;
}
