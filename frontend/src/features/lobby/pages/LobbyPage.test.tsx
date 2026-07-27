import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LobbyState } from '../model/lobbyState';
import type { LobbyBootstrap, LobbySection } from '../model/lobbyTypes';

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  state: null as LobbyState | null,
  quality: 'STANDARD' as 'STANDARD' | 'LOW' | 'REDUCED',
}));

vi.mock('../../../auth/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: '3d2c4040-66f6-45b7-9235-1d5d7a4d4586',
      profile: {
        displayName: 'Hiệp Sĩ',
        onboardingStatus: 'COMPLETED',
        version: 2,
      },
    },
    logout: mocks.logout,
  }),
}));
vi.mock('../hooks/useLobbyBootstrap', () => ({
  useLobbyBootstrap: () => ({
    state: mocks.state,
    retry: vi.fn(),
  }),
}));
vi.mock('../motion/useLobbyQuality', () => ({
  useLobbyQuality: () => mocks.quality,
  useLobbyParallax: vi.fn(),
}));

import LobbyPage from './LobbyPage';

const id = '3d2c4040-66f6-45b7-9235-1d5d7a4d4586';
const secondId = 'b8953c61-67cb-44bf-ac85-0ab9d620d510';
const contentRef = {
  publicationId: id,
  contentVersionId: secondId,
  version: 1,
  checksum: 'a'.repeat(64),
};

function sectionBase(key: string, order: number) {
  return { key, order, contentRef, assets: [] };
}

const sections: LobbySection[] = [
  {
    ...sectionBase('announcement', 1),
    type: 'ANNOUNCEMENT_STRIP',
    announcements: [{ key: 'notice', copy: 'Trại raid đang mở.', tone: 'INFO', order: 1 }],
  },
  {
    ...sectionBase('hero', 2),
    type: 'HERO_BANNER',
    title: 'Tín hiệu tiền tuyến',
    copy: 'Nội dung này đến từ bootstrap đã kiểm tra.',
    tone: 'INFO',
    presentationVariant: 'WIDE',
  },
  {
    ...sectionBase('raids', 3),
    type: 'BOSS_ROOM_LIST',
    title: 'Boss raid đã xuất bản',
    bosses: [{
      bossCode: 'published.boss',
      name: 'Boss đã được duyệt',
      summary: 'Presentation only, không giả room.',
      difficultyLabel: 'Khó',
    }],
  },
  {
    ...sectionBase('player', 4),
    type: 'PLAYER_SUMMARY',
    label: 'Cờ hiệu hiện tại',
  },
  {
    ...sectionBase('character', 5),
    type: 'CHARACTER_PREVIEW',
    title: 'Nhân vật',
  },
  {
    ...sectionBase('inventory', 6),
    type: 'INVENTORY_PREVIEW',
    title: 'Trang bị',
    emptyCopy: 'Chưa có vật phẩm được xuất bản.',
    maxItems: 0,
  },
  {
    ...sectionBase('event', 7),
    type: 'EVENT_SPOTLIGHT',
    title: 'Tiêu điểm',
    copy: 'Không có countdown giả.',
    tone: 'CELEBRATION',
  },
];

function bootstrap(contentSections: LobbySection[] = sections): LobbyBootstrap {
  return {
    schemaVersion: 1,
    generatedAt: '2026-07-27T12:00:00Z',
    locale: 'vi-VN',
    player: { displayName: 'Hiệp Sĩ', profileVersion: 2 },
    navigation: [
      { key: 'home', label: 'Sảnh', iconKey: 'HOME', target: 'LOBBY', order: 1 },
      { key: 'bag', label: 'Túi', iconKey: 'BACKPACK', target: 'INVENTORY', order: 2 },
      { key: 'settings', label: 'Cài đặt', iconKey: 'SETTINGS', target: 'SETTINGS', order: 3 },
    ],
    sections: contentSections,
    nextContentBoundaryAt: null,
    degraded: { active: false, codes: [] },
  };
}

beforeEach(() => {
  mocks.state = { status: 'EMPTY', content: bootstrap([]), degradedCodes: [] };
  mocks.quality = 'STANDARD';
  mocks.logout.mockReset();
});
describe('LobbyPage', () => {
  it('renders an intentional authenticated empty state without internal identity leakage', () => {
    render(<LobbyPage />);

    expect(screen.getByRole('heading', { name: /choose a boss raid/i })).toBeInTheDocument();
    expect(screen.getByText('Hiệp Sĩ')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /no raids are published/i })).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/g_[a-z0-9]+|hero@example/i);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('maps every registered section and never implies a live join action', () => {
    mocks.state = { status: 'READY', content: bootstrap(), degradedCodes: [] };
    render(<LobbyPage />);

    expect(screen.getByRole('heading', { name: 'Tín hiệu tiền tuyến' })).toBeInTheDocument();
    expect(screen.getByText('Trại raid đang mở.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Boss raid đã xuất bản' })).toBeInTheDocument();
    expect(screen.getByText('Boss đã được duyệt')).toBeInTheDocument();
    expect(screen.getByText(/live raid entry is not available yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /join/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('uses one consistent disabled policy for unavailable navigation targets', () => {
    mocks.state = { status: 'READY', content: bootstrap(), degradedCodes: [] };
    render(<LobbyPage />);

    expect(screen.getByRole('button', { name: 'Sảnh' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: /Túi, coming later/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Cài đặt, coming later/i })).toBeDisabled();
  });

  it.each([
    ['BOOTSTRAPPING', /reading the raid board/i],
    ['OFFLINE', /raid signal is offline/i],
    ['RETRYING', /calling the raid board again/i],
    ['AUTH_REQUIRED', /sign in again/i],
    ['ONBOARDING_REQUIRED', /finish your raid identity/i],
    ['FATAL_ERROR', /raid board could not be opened/i],
  ] as const)('renders the %s state safely', (status, heading) => {
    mocks.state = { status, content: null, degradedCodes: [] };
    render(<LobbyPage />);
    expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument();
  });

  it('exposes accessible icon controls and applies the selected quality tier', async () => {
    mocks.quality = 'LOW';
    render(<LobbyPage />);

    const root = document.querySelector('.tc-lobby');
    expect(root).toHaveAttribute('data-quality', 'LOW');
    expect(screen.getByRole('button', { name: /settings, coming later/i })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /log out/i }));
    expect(mocks.logout).toHaveBeenCalledTimes(1);
  });
});
