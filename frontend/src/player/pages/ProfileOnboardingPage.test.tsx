import { StrictMode } from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthProvider';
import { AppRoutes } from '../../app/AppRoutes';
import {
  countDisplayNameGraphemes,
  normalizeDisplayNamePresentation,
} from './ProfileOnboardingPage';

const csrfCookieName = ['XSRF', 'TOKEN'].join('-');
const csrfValue = ['csrf', 'profile', 'value'].join('-');
const accessValue = ['access', 'profile', 'value'].join('-');
const generatedInternalName = ['g', 'internal', 'identity'].join('_');
const incompleteUser = {
  id: '3d2c4040-66f6-45b7-9235-1d5d7a4d4586',
  email: 'hero@example.com',
  role: 'PLAYER',
  status: 'ACTIVE',
  profile: {
    id: 'b8953c61-67cb-44bf-ac85-0ab9d620d510',
    displayName: null,
    onboardingStatus: 'REQUIRED' as const,
    version: 0,
  },
};

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json', ...(init.headers ?? {}) },
    ...init,
  });
}

function renderOnboarding(strict = false) {
  window.history.replaceState({}, '', '/onboarding');
  const app = (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  );
  render(strict ? <StrictMode>{app}</StrictMode> : app);
}

function installSessionFetch(onSubmit?: (body: string) => Response | Promise<Response>) {
  let submitCount = 0;
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url.endsWith('/api/auth/csrf')) {
      document.cookie = `${csrfCookieName}=${csrfValue}; path=/`;
      return new Response(null, { status: 204 });
    }
    if (url.endsWith('/api/auth/refresh')) {
      return jsonResponse({
        accessToken: accessValue,
        accessTokenExpiresAt: '2026-07-27T12:00:00Z',
        user: incompleteUser,
      });
    }
    if (url.endsWith('/api/auth/me')) {
      return jsonResponse(incompleteUser);
    }
    if (url.endsWith('/api/player/profile/onboarding')) {
      submitCount += 1;
      expect(init?.credentials).toBe('include');
      expect(new Headers(init?.headers).get('X-XSRF-TOKEN')).toBe(csrfValue);
      return onSubmit?.(String(init?.body)) ?? jsonResponse({
        ...incompleteUser.profile,
        displayName: 'Raid Hero',
        onboardingStatus: 'COMPLETED',
        version: 1,
      });
    }
    if (url.endsWith('/api/auth/logout')) {
      return new Response(null, { status: 204 });
    }
    throw new Error(`Unexpected request ${url}`);
  });
  return () => submitCount;
}

describe('profile onboarding', () => {
  it('restores an incomplete session without flashing an internal username', async () => {
    installSessionFetch();
    renderOnboarding(true);

    expect(await screen.findByRole('heading', { name: /choose your raid name/i })).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(generatedInternalName);
    expect(window.location.pathname).toBe('/onboarding');
  });

  it('submits once for rapid click and enter then replaces history with app', async () => {
    let release!: () => void;
    const count = installSessionFetch(async () => {
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      return jsonResponse({
        ...incompleteUser.profile,
        displayName: 'Raid Hero',
        onboardingStatus: 'COMPLETED',
        version: 1,
      });
    });
    renderOnboarding();
    const input = await screen.findByLabelText(/display name/i);
    await userEvent.type(input, 'Raid Hero');
    const submit = screen.getByRole('button', { name: /enter the raid camp/i });
    await Promise.all([
      userEvent.click(submit),
      userEvent.keyboard('{Enter}'),
    ]);
    expect(count()).toBe(1);
    release();

    await waitFor(() => expect(window.location.pathname).toBe('/app'));
    expect(await screen.findByRole('heading', { name: /ready, raid hero/i })).toBeInTheDocument();
  });

  it('preserves the candidate and returns focus for a generic collision', async () => {
    installSessionFetch(() => jsonResponse({
      title: 'DISPLAY_NAME_UNAVAILABLE',
      detail: 'Display name is unavailable',
      status: 409,
    }, {
      status: 409,
      headers: { 'content-type': 'application/problem+json' },
    }));
    renderOnboarding();
    const input = await screen.findByLabelText(/display name/i);
    await userEvent.type(input, 'Raid Hero');
    await userEvent.click(screen.getByRole('button', { name: /enter the raid camp/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/unavailable/i);
    expect(input).toHaveValue('Raid Hero');
    expect(input).toHaveFocus();
  });

  it('shows grapheme boundaries and rejects short input without a request', async () => {
    const count = installSessionFetch();
    renderOnboarding();
    const input = await screen.findByLabelText(/display name/i);
    await userEvent.type(input, 'A\u0301B');
    expect(screen.getByText('2/24')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /enter the raid camp/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/between 3 and 24/i);
    expect(count()).toBe(0);
    expect(input).toHaveFocus();
  });

  it('keeps logout available for incomplete accounts', async () => {
    installSessionFetch();
    renderOnboarding();
    await screen.findByRole('heading', { name: /choose your raid name/i });
    await userEvent.click(screen.getByRole('button', { name: /log out/i }));

    await waitFor(() => expect(window.location.pathname).toBe('/login'));
  });

  it('keeps grapheme counting correct without Intl Segmenter', () => {
    const descriptor = Object.getOwnPropertyDescriptor(Intl, 'Segmenter');
    Object.defineProperty(Intl, 'Segmenter', { configurable: true, value: undefined });
    try {
      expect(countDisplayNameGraphemes('A\u0301nh')).toBe(3);
      expect(countDisplayNameGraphemes('a\u0301'.repeat(24))).toBe(24);
      expect(countDisplayNameGraphemes('e\u0301\u0323')).toBe(1);
    } finally {
      if (descriptor) {
        Object.defineProperty(Intl, 'Segmenter', descriptor);
      } else {
        Reflect.deleteProperty(Intl, 'Segmenter');
      }
    }
  });

  it('normalizes Unicode whitespace for presentation without changing the raw input', async () => {
    expect(normalizeDisplayNamePresentation('  Raid   Hero  ')).toBe('Raid Hero');
    expect(normalizeDisplayNamePresentation('\u00A0Ra\u0301id\u2002\u00A0Hu\u0300ng\u00A0'))
      .toBe('Ra\u0301id Hu\u0300ng');

    installSessionFetch();
    renderOnboarding();
    const input = await screen.findByLabelText(/display name/i);
    await userEvent.type(input, '\u00A0Raid\u2002\u00A0Hero\u00A0');

    expect(input).toHaveValue('\u00A0Raid\u2002\u00A0Hero\u00A0');
    expect(screen.getByText('Raid Hero')).toBeInTheDocument();
    expect(screen.getByText('9/24')).toBeInTheDocument();
  });

  it('keeps the user logged out when an old onboarding response arrives late', async () => {
    let releaseOnboarding!: () => void;
    let markOnboardingStarted!: () => void;
    const onboardingStarted = new Promise<void>((resolve) => {
      markOnboardingStarted = resolve;
    });
    installSessionFetch(async () => {
      markOnboardingStarted();
      await new Promise<void>((resolve) => {
        releaseOnboarding = resolve;
      });
      return jsonResponse({
        ...incompleteUser.profile,
        displayName: 'Late Hero',
        onboardingStatus: 'COMPLETED',
        version: 1,
      });
    });
    renderOnboarding();
    const input = await screen.findByLabelText(/display name/i);
    await userEvent.type(input, 'Late Hero');
    await userEvent.click(screen.getByRole('button', { name: /enter the raid camp/i }));
    await onboardingStarted;

    const logoutButton = screen.getByRole('button', { name: /log out/i });
    expect(logoutButton).toBeEnabled();
    await userEvent.click(logoutButton);
    await waitFor(() => expect(window.location.pathname).toBe('/login'));
    releaseOnboarding();

    await waitFor(() => expect(screen.getByRole('heading', { name: /enter the camp/i })).toBeInTheDocument());
    expect(window.location.pathname).toBe('/login');
    expect(screen.queryByText(/late hero/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/raid lobby online/i)).not.toBeInTheDocument();
  });
});
