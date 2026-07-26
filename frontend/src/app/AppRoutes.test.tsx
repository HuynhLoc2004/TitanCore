import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { AppRoutes } from './AppRoutes';

const user = {
  id: '3d2c4040-66f6-45b7-9235-1d5d7a4d4586',
  email: 'hero@example.com',
  username: 'hero',
  role: 'PLAYER',
  status: 'ACTIVE',
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

function renderApp(path = '/login') {
  window.history.replaceState({}, '', path);
  render(
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>,
  );
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

    expect(await screen.findByRole('heading', { name: /welcome back, hero/i })).toBeInTheDocument();
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
    await screen.findByRole('heading', { name: /welcome back, hero/i });
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
    expect(await screen.findByRole('heading', { name: /welcome back, hero/i })).toBeInTheDocument();
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
    expect(await screen.findByRole('heading', { name: /welcome back, hero/i })).toBeInTheDocument();
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
    expect(screen.queryByRole('heading', { name: /welcome back/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /enter the camp/i })).not.toBeInTheDocument();
  });
});
