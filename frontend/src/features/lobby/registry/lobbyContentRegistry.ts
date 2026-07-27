import type {
  LobbyAsset,
  LobbyBootstrap,
  LobbyContentRef,
  LobbyDegradedCode,
  LobbyIconKey,
  LobbyNavigationTarget,
  LobbyPresentationVariant,
  LobbySection,
  LobbyTone,
  ParsedLobbyBootstrap,
  SupportedLobbyLocale,
} from '../model/lobbyTypes';

const KEY = /^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SHA256 = /^[0-9a-f]{64}$/;
const LOCALES = new Set<SupportedLobbyLocale>(['vi-VN', 'en-US']);
const TARGETS = new Set<LobbyNavigationTarget>(['LOBBY', 'INVENTORY', 'SETTINGS']);
const ICONS = new Set<LobbyIconKey>(['HOME', 'BACKPACK', 'SETTINGS']);
const TONES = new Set<LobbyTone>(['NEUTRAL', 'INFO', 'CELEBRATION', 'WARNING']);
const VARIANTS = new Set<LobbyPresentationVariant>(['WIDE', 'COMPACT']);
const SECTION_TYPES = new Set([
  'HERO_BANNER',
  'ANNOUNCEMENT_STRIP',
  'BOSS_ROOM_LIST',
  'PLAYER_SUMMARY',
  'CHARACTER_PREVIEW',
  'INVENTORY_PREVIEW',
  'EVENT_SPOTLIGHT',
]);
const SERVER_DEGRADED_CODES = new Set<LobbyDegradedCode>([
  'UNKNOWN_CONTENT_SCHEMA',
  'INVALID_CONTENT',
  'MISSING_ASSET',
  'CONTENT_UNAVAILABLE',
]);

export class InvalidLobbyBootstrapError extends Error {
  constructor() {
    super('Lobby response did not match the approved contract');
  }
}

export function parseLobbyBootstrap(input: unknown): ParsedLobbyBootstrap {
  const root = record(input);
  if (root.schemaVersion !== 1) {
    throw new InvalidLobbyBootstrapError();
  }
  const locale = text(root.locale, 5) as SupportedLobbyLocale;
  if (!LOCALES.has(locale)) {
    throw new InvalidLobbyBootstrapError();
  }
  const player = record(root.player);
  const rawNavigation = array(root.navigation, 7);
  const rawSections = array(root.sections, 7);
  const clientCodes: LobbyDegradedCode[] = [];

  const navigation = deduplicate(rawNavigation.flatMap((item) => {
    try {
      const value = record(item);
      const target = text(value.target, 16) as LobbyNavigationTarget;
      const iconKey = text(value.iconKey, 16) as LobbyIconKey;
      if (!TARGETS.has(target) || !ICONS.has(iconKey)) {
        throw new InvalidLobbyBootstrapError();
      }
      return [{
        key: key(value.key),
        label: text(value.label, 64),
        iconKey,
        target,
        order: integer(value.order, 0, 1000),
      }];
    } catch {
      pushCode(clientCodes, 'CLIENT_INVALID_NAVIGATION');
      return [];
    }
  }).sort(orderThenKey), clientCodes, 'CLIENT_INVALID_NAVIGATION');

  const sections = deduplicate(rawSections.flatMap((item) => {
    try {
      return [parseSection(item)];
    } catch {
      pushCode(clientCodes, 'CLIENT_INVALID_SECTION');
      return [];
    }
  }).sort(orderThenKey), clientCodes, 'CLIENT_INVALID_SECTION');
  if (sections.reduce((total, section) => total + section.assets.length, 0) > 24) {
    throw new InvalidLobbyBootstrapError();
  }

  const degraded = record(root.degraded);
  const codes = array(degraded.codes, 8).flatMap((code) => {
    if (typeof code === 'string' && SERVER_DEGRADED_CODES.has(code as LobbyDegradedCode)) {
      return [code as LobbyDegradedCode];
    }
    return [];
  });
  const nextBoundary = nullableDate(root.nextContentBoundaryAt);

  return {
    content: {
      schemaVersion: 1,
      generatedAt: date(root.generatedAt),
      locale,
      player: {
        displayName: text(player.displayName, 24),
        profileVersion: integer(player.profileVersion, 0, Number.MAX_SAFE_INTEGER),
      },
      navigation,
      sections,
      nextContentBoundaryAt: nextBoundary,
      degraded: {
        active: boolean(degraded.active),
        codes,
      },
    },
    clientDegradedCodes: clientCodes,
  };
}

function parseSection(input: unknown): LobbySection {
  const value = record(input);
  const type = text(value.type, 32);
  if (!SECTION_TYPES.has(type)) {
    throw new InvalidLobbyBootstrapError();
  }
  const parsedAssets = assets(value.assets);
  const base = {
    key: key(value.key),
    order: integer(value.order, 0, 1000),
    contentRef: contentRef(value.contentRef),
  };
  switch (type) {
    case 'HERO_BANNER': {
      const tone = enumValue(value.tone, TONES);
      const presentationVariant = enumValue(value.presentationVariant, VARIANTS);
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, ['HERO', 'MOBILE', 'DESKTOP'], 2),
        type,
        title: text(value.title, 96),
        copy: text(value.copy, 500),
        tone,
        presentationVariant,
      };
    }
    case 'ANNOUNCEMENT_STRIP':
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, [], 0),
        type,
        announcements: array(value.announcements, 3).map((announcement) => {
          const item = record(announcement);
          return {
            key: key(item.key),
            copy: text(item.copy, 240),
            tone: enumValue(item.tone, TONES),
            order: integer(item.order, 0, 1000),
          };
        }).sort(orderThenKey),
      };
    case 'BOSS_ROOM_LIST':
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, ['CARD', 'THUMBNAIL', 'PORTRAIT'], 6),
        type,
        title: text(value.title, 96),
        bosses: array(value.bosses, 6).map((boss) => {
          const item = record(boss);
          return {
            bossCode: key(item.bossCode),
            name: text(item.name, 64),
            summary: text(item.summary, 240),
            difficultyLabel: text(item.difficultyLabel, 32),
          };
        }),
      };
    case 'PLAYER_SUMMARY':
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, [], 0),
        type,
        label: text(value.label, 96),
      };
    case 'CHARACTER_PREVIEW':
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, ['CARD', 'THUMBNAIL', 'PORTRAIT'], 1),
        type,
        title: text(value.title, 96),
      };
    case 'INVENTORY_PREVIEW':
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, [], 0),
        type,
        title: text(value.title, 96),
        emptyCopy: text(value.emptyCopy, 160),
        maxItems: integer(value.maxItems, 0, 6),
      };
    case 'EVENT_SPOTLIGHT':
      return {
        ...base,
        assets: compatibleAssets(parsedAssets, ['HERO', 'MOBILE', 'DESKTOP'], 1),
        type,
        title: text(value.title, 96),
        copy: text(value.copy, 500),
        tone: enumValue(value.tone, TONES),
      };
    default:
      throw new InvalidLobbyBootstrapError();
  }
}

function assets(input: unknown): LobbyAsset[] {
  return array(input, 6).map((asset) => {
    const value = record(asset);
    const mediaType = text(value.mediaType, 32);
    if (!['image/avif', 'image/jpeg', 'image/png', 'image/webp'].includes(mediaType)) {
      throw new InvalidLobbyBootstrapError();
    }
    if (value.deliveryUrl !== null) {
      throw new InvalidLobbyBootstrapError();
    }
    return {
      assetId: uuid(value.assetId),
      variantKey: text(value.variantKey, 32),
      mediaType,
      width: integer(value.width, 1, 16384),
      height: integer(value.height, 1, 16384),
      durationMillis: value.durationMillis === null
        ? null
        : integer(value.durationMillis, 0, 86_400_000),
      checksum: checksum(value.checksum),
      deliveryUrl: null,
    };
  });
}

function compatibleAssets(
  values: LobbyAsset[],
  variants: string[],
  maximum: number,
) {
  if (values.length > maximum || values.some((asset) => !variants.includes(asset.variantKey))) {
    throw new InvalidLobbyBootstrapError();
  }
  return values;
}

function contentRef(input: unknown): LobbyContentRef {
  const value = record(input);
  return {
    publicationId: uuid(value.publicationId),
    contentVersionId: uuid(value.contentVersionId),
    version: integer(value.version, 1, Number.MAX_SAFE_INTEGER),
    checksum: checksum(value.checksum),
  };
}

function record(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new InvalidLobbyBootstrapError();
  }
  return value as Record<string, unknown>;
}

function array(value: unknown, maximum: number): unknown[] {
  if (!Array.isArray(value) || value.length > maximum) {
    throw new InvalidLobbyBootstrapError();
  }
  return value;
}

function text(value: unknown, maximum: number): string {
  if (typeof value !== 'string' || value.trim().length === 0 || value.length > maximum) {
    throw new InvalidLobbyBootstrapError();
  }
  return value.trim();
}

function key(value: unknown): string {
  const result = text(value, 64);
  if (!KEY.test(result)) {
    throw new InvalidLobbyBootstrapError();
  }
  return result;
}

function integer(value: unknown, minimum: number, maximum: number): number {
  if (!Number.isSafeInteger(value) || (value as number) < minimum || (value as number) > maximum) {
    throw new InvalidLobbyBootstrapError();
  }
  return value as number;
}

function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') {
    throw new InvalidLobbyBootstrapError();
  }
  return value;
}

function date(value: unknown): string {
  const result = text(value, 64);
  if (!Number.isFinite(Date.parse(result))) {
    throw new InvalidLobbyBootstrapError();
  }
  return result;
}

function nullableDate(value: unknown): string | null {
  return value === null ? null : date(value);
}

function uuid(value: unknown): string {
  const result = text(value, 36);
  if (!UUID.test(result)) {
    throw new InvalidLobbyBootstrapError();
  }
  return result;
}

function checksum(value: unknown): string {
  const result = text(value, 64);
  if (!SHA256.test(result)) {
    throw new InvalidLobbyBootstrapError();
  }
  return result;
}

function enumValue<T extends string>(value: unknown, allowed: Set<T>): T {
  const result = text(value, 32) as T;
  if (!allowed.has(result)) {
    throw new InvalidLobbyBootstrapError();
  }
  return result;
}

function orderThenKey<T extends { order: number; key: string }>(left: T, right: T) {
  return left.order - right.order || left.key.localeCompare(right.key);
}

function pushCode(codes: LobbyDegradedCode[], code: LobbyDegradedCode) {
  if (!codes.includes(code) && codes.length < 8) {
    codes.push(code);
  }
}

function deduplicate<T extends { key: string }>(
  values: T[],
  codes: LobbyDegradedCode[],
  code: LobbyDegradedCode,
) {
  const seen = new Set<string>();
  return values.filter((value) => {
    if (seen.has(value.key)) {
      pushCode(codes, code);
      return false;
    }
    seen.add(value.key);
    return true;
  });
}
