import { StrictMode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { invalidateAuthGeneration, setAccessToken } from '../auth/api';
import { AppRoutes, navigate } from './AppRoutes';

vi.mock('../game/AnimationLabPage', () => ({
  AnimationLabPage: () => <h1>Motion Forge</h1>,
}));
vi.mock('../game/WorldRuntimePage', () => ({
  WorldRuntimePage: () => <h1>Raid Camp Local Khu</h1>,
}));

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
const incompleteUser = {
  ...user,
  profile: {
    ...user.profile,
    displayName: null,
    onboardingStatus: 'REQUIRED' as const,
    version: 0,
  },
};
const csrfCookieName = ['XSRF', 'TOKEN'].join('-');
const csrfValue = ['csrf', 'test', 'value'].join('-');
const restoredAccess = ['restored', 'access', 'value'].join('-');

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json', ...(init.headers ?? {}) },
    ...init,
  });
}

function renderApp(path = '/login', strict = false) {
  window.history.replaceState({}, '', path);
  const app = (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  );
  render(strict ? <StrictMode>{app}</StrictMode> : app);
}

function installSuccessfulSessionFetch() {
  let refreshCount = 0;
  let meCount = 0;
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.endsWith('/api/auth/csrf')) {
      document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
      return new Response(null, { status: 204 });
    }
    if (url.endsWith('/api/auth/refresh')) {
      refreshCount += 1;
      return jsonResponse({
        accessToken: restoredAccess,
        accessTokenExpiresAt: '2026-07-26T12:00:00Z',
        user,
      });
    }
    if (url.endsWith('/api/auth/me')) {
      meCount += 1;
      return jsonResponse(user);
    }
    throw new Error(`Unexpected request ${url}`);
  });
  return {
    fetchMock,
    counts: () => ({ refreshCount, meCount }),
  };
}

function installSessionFor(sessionUser: typeof user | typeof incompleteUser) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.endsWith('/api/auth/csrf')) {
      document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
      return new Response(null, { status: 204 });
    }
    if (url.endsWith('/api/auth/refresh')) {
      return jsonResponse({
        accessToken: restoredAccess,
        accessTokenExpiresAt: '2026-07-26T12:00:00Z',
        user: sessionUser,
      });
    }
    if (url.endsWith('/api/auth/me')) {
      return jsonResponse(sessionUser);
    }
    throw new Error(`Unexpected request ${url}`);
  });
}

describe('auth routes', () => {
  it('restores an existing cookie session without storing tokens in browser storage', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: restoredAccess,
          accessTokenExpiresAt: '2026-07-26T12:00:00Z',
          user,
        });
      }
      if (url.endsWith('/api/auth/me')) {
        return jsonResponse(user);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/app');

    expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('redirects protected routes to login when refresh fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return new Response(null, { status: 204 });
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/app');

    expect(await screen.findByRole('heading', { name: /enter the camp/i })).toBeInTheDocument();
    await waitFor(() => expect(window.location.pathname).toBe('/login'));
  });

  it('allows a completed authenticated player to open the animation proof', async () => {
    installSessionFor(user);

    renderApp('/animation-lab');

    expect(await screen.findByRole('heading', { name: /motion forge/i })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/animation-lab');
  });

  it('does not render the animation proof for an anonymous player', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return new Response(null, { status: 204 });
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/animation-lab');

    expect(await screen.findByRole('heading', { name: /enter the camp/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /motion forge/i })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
  });

  it('protects the local world runtime behind authentication and onboarding', async () => {
    installSessionFor(user);
    renderApp('/world-lab');

    expect(await screen.findByRole('heading', { name: /raid camp local khu/i }))
      .toBeInTheDocument();
    expect(window.location.pathname).toBe('/world-lab');
  });

  it('does not initialize the local world runtime for an anonymous player', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        return new Response(null, { status: 204 });
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/world-lab');

    expect(await screen.findByRole('heading', { name: /enter the camp/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /raid camp local khu/i })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
  });

  it('sends an incomplete player to onboarding before the local world runtime', async () => {
    installSessionFor(incompleteUser);
    renderApp('/world-lab');

    expect(await screen.findByRole('heading', { name: /choose your raid name/i }))
      .toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /raid camp local khu/i })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/onboarding');
  });

  it('clears the authenticated shell after logout', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: restoredAccess,
          accessTokenExpiresAt: '2026-07-26T12:00:00Z',
          user,
        });
      }
      if (url.endsWith('/api/auth/me')) {
        return jsonResponse(user);
      }
      if (url.endsWith('/api/auth/logout')) {
        return new Response(null, { status: 204 });
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/app');
    await screen.findByRole('heading', { name: /ready, hero/i });
    await userEvent.click(screen.getByRole('button', { name: /log out/i }));

    await waitFor(() => expect(screen.getByRole('heading', { name: /enter the camp/i })).toBeInTheDocument());
    expect(window.location.pathname).toBe('/login');
  });

  it('does not render the login form for an authenticated user on a public login route', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: restoredAccess,
          accessTokenExpiresAt: '2026-07-26T12:00:00Z',
          user,
        });
      }
      if (url.endsWith('/api/auth/me')) {
        return jsonResponse(user);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/login');

    await waitFor(() => expect(window.location.pathname).toBe('/app'));
    expect(screen.queryByRole('heading', { name: /enter the camp/i })).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
  });

  it('does not render the registration form for an authenticated user on a public register route', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        return jsonResponse({
          accessToken: restoredAccess,
          accessTokenExpiresAt: '2026-07-26T12:00:00Z',
          user,
        });
      }
      if (url.endsWith('/api/auth/me')) {
        return jsonResponse(user);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/register');

    await waitFor(() => expect(window.location.pathname).toBe('/app'));
    expect(screen.queryByRole('heading', { name: /forge your banner/i })).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
  });

  it('renders only the loading shell while bootstrap is pending', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        return new Promise<Response>(() => undefined);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/app');

    expect(screen.getByRole('status')).toHaveTextContent(/checking your raid pass/i);
    expect(screen.queryByRole('heading', { name: /ready/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /enter the camp/i })).not.toBeInTheDocument();
  });

  it('never renders completed-profile content when an incomplete account opens app', async () => {
    installSessionFor(incompleteUser);
    const renderedText: string[] = [];
    const observer = new MutationObserver(() => renderedText.push(document.body.textContent ?? ''));
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    let navigationCount = 0;
    const countNavigation = () => {
      navigationCount += 1;
    };
    window.addEventListener('popstate', countNavigation);
    try {
      renderApp('/app', true);
      expect(await screen.findByRole('heading', { name: /choose your raid name/i })).toBeInTheDocument();
      expect(window.location.pathname).toBe('/onboarding');
      expect(renderedText.some((text) => /the portal is awake|ready,/i.test(text))).toBe(false);
      expect(navigationCount).toBe(1);
    } finally {
      observer.disconnect();
      window.removeEventListener('popstate', countNavigation);
    }
  });

  it('does not flash completed content from another completed-only route', async () => {
    installSessionFor(incompleteUser);
    const renderedText: string[] = [];
    const observer = new MutationObserver(() => renderedText.push(document.body.textContent ?? ''));
    observer.observe(document.body, { childList: true, subtree: true, characterData: true });
    try {
      renderApp('/inventory');
      expect(await screen.findByRole('heading', { name: /choose your raid name/i })).toBeInTheDocument();
      expect(renderedText.some((text) => /the portal is awake|ready,/i.test(text))).toBe(false);
    } finally {
      observer.disconnect();
    }
  });

  it('never renders onboarding for a completed account opening onboarding', async () => {
    installSessionFor(user);
    renderApp('/onboarding');

    expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /choose your raid name/i })).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/app');
  });

  it('restores an OAuth success callback through refresh and me before routing to app', async () => {
    const { counts } = installSuccessfulSessionFetch();

    renderApp('/auth/oauth/callback?oauth=success&code=do-not-read&state=do-not-read');

    expect(screen.getByRole('status')).toHaveTextContent(/restoring your raid pass/i);
    expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/app');
    expect(window.location.search).toBe('');
    expect(counts()).toEqual({ refreshCount: 1, meCount: 1 });
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('restores the OAuth callback under React Strict Mode without duplicate requests', async () => {
    const { counts } = installSuccessfulSessionFetch();
    let navigationCount = 0;
    const countNavigation = () => {
      navigationCount += 1;
    };
    window.addEventListener('popstate', countNavigation);

    try {
      renderApp('/auth/oauth/callback?oauth=success', true);

      expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
      expect(screen.queryByText(/restoring your raid pass/i)).not.toBeInTheDocument();
      expect(window.location.pathname).toBe('/app');
      expect(window.location.search).toBe('');
      expect(navigationCount).toBe(1);
      expect(counts()).toEqual({ refreshCount: 1, meCount: 1 });
    } finally {
      window.removeEventListener('popstate', countNavigation);
    }
  });

  it('shows the safe callback failure under React Strict Mode and removes its parameters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');

    renderApp('/auth/oauth/callback?oauth=failed&code=collision', true);

    expect(await screen.findByRole('alert')).toHaveTextContent(/local login first/i);
    expect(window.location.pathname).toBe('/auth/oauth/callback');
    expect(window.location.search).toBe('');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows a safe OAuth failure message and removes query parameters from history', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 500 }));

    renderApp('/auth/oauth/callback?oauth=failed&code=collision&error_description=raw-provider-detail');

    expect(await screen.findByRole('alert')).toHaveTextContent(/local login first/i);
    expect(screen.getByRole('alert')).not.toHaveTextContent(/raw-provider-detail/i);
    expect(window.location.pathname).toBe('/auth/oauth/callback');
    expect(window.location.search).toBe('');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('uses a generic OAuth failure message for unknown callback codes', async () => {
    renderApp('/auth/oauth/callback?oauth=failed&code=very_detailed_backend_value');

    expect(await screen.findByRole('alert')).toHaveTextContent(/did not finish/i);
    expect(screen.getByRole('alert')).not.toHaveTextContent(/very_detailed_backend_value/i);
  });

  it('does not parse or persist token-like OAuth callback parameters', async () => {
    installSuccessfulSessionFetch();

    renderApp('/auth/oauth/callback?oauth=success&access_token=url-value&refresh_token=url-value&id_token=url-value');

    expect(await screen.findByRole('heading', { name: /ready, hero/i })).toBeInTheDocument();
    expect(window.location.href).not.toContain('url-value');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('does not resurrect authentication when logout invalidates a late OAuth restore', async () => {
    let releaseRefresh!: () => void;
    let markRefreshStarted!: () => void;
    const refreshStarted = new Promise<void>((resolve) => {
      markRefreshStarted = resolve;
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        markRefreshStarted();
        await new Promise<void>((resolve) => {
          releaseRefresh = resolve;
        });
        return jsonResponse({
          accessToken: restoredAccess,
          accessTokenExpiresAt: '2026-07-26T12:00:00Z',
          user,
        });
      }
      if (url.endsWith('/api/auth/logout')) {
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/me')) {
        return jsonResponse(user);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/auth/oauth/callback?oauth=success');
    await waitFor(() => expect(screen.getByRole('status')).toBeInTheDocument());
    await refreshStarted;
    invalidateAuthGeneration();
    setAccessToken(null);
    releaseRefresh();

    expect(await screen.findByRole('alert')).toHaveTextContent(/did not finish/i);
    expect(screen.queryByRole('heading', { name: /welcome back/i })).not.toBeInTheDocument();
  });

  it('does not update callback UI after the callback component unmounts', async () => {
    let resolveRefresh!: (response: Response) => void;
    let markRefreshStarted!: () => void;
    const refreshStarted = new Promise<void>((resolve) => {
      markRefreshStarted = resolve;
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf')) {
        document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/api/auth/refresh')) {
        markRefreshStarted();
        return new Promise<Response>((resolve) => {
          resolveRefresh = resolve;
        });
      }
      if (url.endsWith('/api/auth/me')) {
        return jsonResponse(user);
      }
      throw new Error(`Unexpected request ${url}`);
    });

    renderApp('/auth/oauth/callback?oauth=success');
    await refreshStarted;
    navigate('/login', { replace: true });
    resolveRefresh(jsonResponse({
      accessToken: restoredAccess,
      accessTokenExpiresAt: '2026-07-26T12:00:00Z',
      user,
    }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });
});
