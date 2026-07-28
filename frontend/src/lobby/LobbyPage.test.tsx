import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LobbyPage } from './LobbyPage';

const user = {
  id: '3d2c4040-66f6-45b7-9235-1d5d7a4d4586',
  email: 'hero@example.com',
  role: 'PLAYER',
  status: 'ACTIVE',
  profile: {
    id: 'b8953c61-67cb-44bf-ac85-0ab9d620d510',
    displayName: 'Anh Hung',
    onboardingStatus: 'COMPLETED' as const,
    version: 1,
  },
};

function bootstrapResponse(overrides: Record<string, unknown> = {}) {
  return new Response(JSON.stringify({
    schemaVersion: 1,
    generatedAt: '2026-07-28T08:00:00Z',
    locale: 'vi-VN',
    player: { displayName: 'Anh Hung', profileVersion: 1 },
    navigation: [
      { key: 'lobby', label: 'Camp', iconKey: 'CAMP', target: 'LOBBY', order: 1 },
      { key: 'inventory', label: 'Inventory', iconKey: 'PACK', target: 'INVENTORY', order: 2 },
    ],
    sections: [],
    nextContentBoundaryAt: null,
    degraded: { active: false, codes: [] },
    ...overrides,
  }), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

describe('Raid Camp lobby', () => {
  it('renders authenticated identity and an honest empty state without generated data', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(bootstrapResponse());

    render(<LobbyPage user={user} onLogout={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: /ready, anh hung/i })).toBeInTheDocument();
    expect(screen.getByText(/no raid dispatches yet/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /inventory/i })).toBeDisabled();
    expect(screen.queryByText(/players online|reward|ends in/i)).not.toBeInTheDocument();
    expect(document.querySelector('.tc-camp-atmosphere')).toHaveAttribute('aria-hidden', 'true');
    expect(document.querySelectorAll('.tc-camp-embers span')).toHaveLength(10);
  });

  it('renders only allowlisted typed bootstrap sections and safe remote asset URLs', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(bootstrapResponse({
      sections: [
        {
          key: 'hero',
          type: 'HERO_BANNER',
          order: 1,
          title: 'Portal Patrol',
          copy: 'The campfire is warm and the portal is behaving suspiciously.',
          tone: 'INFO',
          presentationVariant: 'CAMP',
          assets: [{
            assetId: 'asset-1',
            variantKey: 'HERO',
            mediaType: 'image/webp',
            width: 800,
            height: 800,
            durationMillis: null,
            checksum: 'a'.repeat(64),
            deliveryUrl: 'https://cdn.example.test/hero.webp',
          }],
        },
        {
          key: 'rooms',
          type: 'BOSS_ROOM_LIST',
          order: 2,
          title: 'Boss dispatches',
          bosses: [{
            bossCode: 'BOSS_ALPHA',
            name: 'Approved Boss',
            summary: 'A server-approved encounter summary.',
            difficultyLabel: 'Warm-up',
          }],
          assets: [],
        },
      ],
    }));

    render(<LobbyPage user={user} onLogout={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: /portal patrol/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /approved boss/i })).toBeInTheDocument();
    const hero = document.querySelector<HTMLImageElement>('.tc-camp-hero__raider');
    expect(hero?.src).toBe('https://cdn.example.test/hero.webp');
  });

  it('uses a safe retry state when bootstrap is unavailable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('private network detail'));

    render(<LobbyPage user={user} onLogout={vi.fn()} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/camp signal dropped/i);
    expect(screen.queryByText(/private network detail/i)).not.toBeInTheDocument();
  });

  it('fails safely for an unsupported bootstrap schema', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(bootstrapResponse({
      schemaVersion: 99,
    }));

    render(<LobbyPage user={user} onLogout={vi.fn()} />);

    expect(await screen.findByRole('alert')).toHaveTextContent(/camp signal dropped/i);
    expect(screen.queryByText(/schema|99/i)).not.toBeInTheDocument();
  });

  it('never uses an executable asset URL', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(bootstrapResponse({
      sections: [{
        key: 'hero',
        type: 'HERO_BANNER',
        order: 1,
        title: 'Safe camp',
        copy: 'Server-approved copy.',
        tone: 'INFO',
        presentationVariant: 'CAMP',
        assets: [{
          assetId: 'asset-unsafe',
          variantKey: 'HERO',
          mediaType: 'image/webp',
          width: 800,
          height: 800,
          durationMillis: null,
          checksum: 'b'.repeat(64),
          deliveryUrl: 'javascript:alert(1)',
        }],
      }],
    }));

    render(<LobbyPage user={user} onLogout={vi.fn()} />);

    await screen.findByRole('heading', { name: /safe camp/i });
    const hero = document.querySelector<HTMLImageElement>('.tc-camp-hero__raider');
    expect(hero?.getAttribute('src')).toBe('/assets/raid-camp/core-raider.png');
  });

  it('aborts bootstrap updates when the page unmounts', async () => {
    let capturedSignal: AbortSignal | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      capturedSignal = init?.signal ?? undefined;
      return new Promise<Response>(() => undefined);
    });

    const view = render(<LobbyPage user={user} onLogout={vi.fn()} />);
    view.unmount();

    await waitFor(() => expect(capturedSignal?.aborted).toBe(true));
  });
});
