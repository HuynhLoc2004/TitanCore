import { describe, expect, it } from 'vitest';
import { parseLobbyBootstrap } from './lobbyContentRegistry';

const id = '3d2c4040-66f6-45b7-9235-1d5d7a4d4586';
const secondId = 'b8953c61-67cb-44bf-ac85-0ab9d620d510';
const checksum = 'a'.repeat(64);

function baseSection(type: string, key: string, order: number) {
  return {
    type,
    key,
    order,
    contentRef: {
      publicationId: id,
      contentVersionId: secondId,
      version: 1,
      checksum,
    },
    assets: [],
  };
}

function response(sections: unknown[] = []) {
  return {
    schemaVersion: 1,
    generatedAt: '2026-07-27T12:00:00Z',
    locale: 'vi-VN',
    player: { displayName: 'Hiệp Sĩ', profileVersion: 4 },
    navigation: [
      { key: 'home', label: 'Sảnh', iconKey: 'HOME', target: 'LOBBY', order: 1 },
      { key: 'bag', label: 'Túi', iconKey: 'BACKPACK', target: 'INVENTORY', order: 2 },
    ],
    sections,
    nextContentBoundaryAt: null,
    degraded: { active: false, codes: [] },
  };
}

describe('lobby content registry', () => {
  it('maps all seven approved section types without rendering raw maps', () => {
    const sections = [
      {
        ...baseSection('HERO_BANNER', 'hero', 1),
        title: 'Tín hiệu tiền tuyến',
        copy: 'Một thông điệp đã được duyệt.',
        tone: 'INFO',
        presentationVariant: 'WIDE',
      },
      {
        ...baseSection('ANNOUNCEMENT_STRIP', 'news', 2),
        announcements: [{ key: 'notice', copy: 'Trại đang mở.', tone: 'NEUTRAL', order: 1 }],
      },
      {
        ...baseSection('BOSS_ROOM_LIST', 'raids', 3),
        title: 'Chọn boss raid',
        bosses: [{
          bossCode: 'stone.king',
          name: 'Tên đã xuất bản',
          summary: 'Nội dung đã xuất bản.',
          difficultyLabel: 'Khó',
        }],
      },
      { ...baseSection('PLAYER_SUMMARY', 'player', 4), label: 'Cờ hiệu' },
      { ...baseSection('CHARACTER_PREVIEW', 'character', 5), title: 'Nhân vật' },
      {
        ...baseSection('INVENTORY_PREVIEW', 'inventory', 6),
        title: 'Trang bị',
        emptyCopy: 'Chưa có vật phẩm đã xuất bản.',
        maxItems: 0,
      },
      {
        ...baseSection('EVENT_SPOTLIGHT', 'event', 7),
        title: 'Tiêu điểm',
        copy: 'Không có bộ đếm giả.',
        tone: 'CELEBRATION',
      },
    ];

    const parsed = parseLobbyBootstrap(response(sections));

    expect(parsed.content.sections.map((section) => section.type)).toEqual([
      'HERO_BANNER',
      'ANNOUNCEMENT_STRIP',
      'BOSS_ROOM_LIST',
      'PLAYER_SUMMARY',
      'CHARACTER_PREVIEW',
      'INVENTORY_PREVIEW',
      'EVENT_SPOTLIGHT',
    ]);
    expect(parsed.clientDegradedCodes).toEqual([]);
  });

  it('omits unknown and invalid optional sections with one bounded safe reason', () => {
    const parsed = parseLobbyBootstrap(response([
      { ...baseSection('REMOTE_REACT_COMPONENT', 'remote', 1), component: '<script />' },
      { ...baseSection('HERO_BANNER', 'hero', 2), title: 'x'.repeat(97) },
    ]));

    expect(parsed.content.sections).toEqual([]);
    expect(parsed.clientDegradedCodes).toEqual(['CLIENT_INVALID_SECTION']);
  });

  it('fails closed for an unknown top-level schema and unsafe navigation targets', () => {
    expect(() => parseLobbyBootstrap({ ...response(), schemaVersion: 2 })).toThrow();
    const unsafe = response();
    unsafe.navigation[0] = {
      ...unsafe.navigation[0],
      target: 'https://malicious.invalid' as 'LOBBY',
    };

    const parsed = parseLobbyBootstrap(unsafe);
    expect(parsed.content.navigation).toHaveLength(1);
    expect(parsed.clientDegradedCodes).toContain('CLIENT_INVALID_NAVIGATION');
  });

  it('rejects non-null delivery URLs until the approved asset-delivery phase', () => {
    const hero = {
      ...baseSection('HERO_BANNER', 'hero', 1),
      title: 'Published',
      copy: 'Text first.',
      tone: 'INFO',
      presentationVariant: 'WIDE',
      assets: [{
        assetId: id,
        variantKey: 'HERO',
        mediaType: 'image/webp',
        width: 1280,
        height: 720,
        durationMillis: null,
        checksum,
        deliveryUrl: 'https://unapproved.invalid/object',
      }],
    };

    const parsed = parseLobbyBootstrap(response([hero]));
    expect(parsed.content.sections).toEqual([]);
    expect(parsed.clientDegradedCodes).toContain('CLIENT_INVALID_SECTION');
  });

  it('enforces maximum count and text bounds', () => {
    const tooMany = response(Array.from({ length: 8 }, (_, index) => ({
      ...baseSection('PLAYER_SUMMARY', `player.${index}`, index),
      label: 'Player',
    })));
    expect(() => parseLobbyBootstrap(tooMany)).toThrow();

    const tooLong = response([{
      ...baseSection('EVENT_SPOTLIGHT', 'event', 1),
      title: 'Event',
      copy: 'x'.repeat(501),
      tone: 'INFO',
    }]);
    expect(parseLobbyBootstrap(tooLong).content.sections).toEqual([]);
  });

  it('enforces section-specific asset variants and duplicate keys', () => {
    const asset = {
      assetId: id,
      variantKey: 'CARD',
      mediaType: 'image/webp',
      width: 640,
      height: 360,
      durationMillis: null,
      checksum,
      deliveryUrl: null,
    };
    const invalidHero = {
      ...baseSection('HERO_BANNER', 'duplicate', 1),
      title: 'Hero',
      copy: 'Published copy',
      tone: 'INFO',
      presentationVariant: 'WIDE',
      assets: [asset],
    };
    const validPlayer = {
      ...baseSection('PLAYER_SUMMARY', 'duplicate', 2),
      label: 'Player',
    };

    const parsed = parseLobbyBootstrap(response([invalidHero, validPlayer, {
      ...validPlayer,
      order: 3,
    }]));

    expect(parsed.content.sections).toHaveLength(1);
    expect(parsed.content.sections[0].type).toBe('PLAYER_SUMMARY');
    expect(parsed.clientDegradedCodes).toEqual(['CLIENT_INVALID_SECTION']);
  });
});
