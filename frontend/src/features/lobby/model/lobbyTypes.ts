export type SupportedLobbyLocale = 'vi-VN' | 'en-US';

export type LobbyNavigationTarget = 'LOBBY' | 'INVENTORY' | 'SETTINGS';
export type LobbyIconKey = 'HOME' | 'BACKPACK' | 'SETTINGS';
export type LobbyTone = 'NEUTRAL' | 'INFO' | 'CELEBRATION' | 'WARNING';
export type LobbyPresentationVariant = 'WIDE' | 'COMPACT';

export type LobbyContentRef = {
  publicationId: string;
  contentVersionId: string;
  version: number;
  checksum: string;
};
export type LobbyAsset = {
  assetId: string;
  variantKey: string;
  mediaType: string;
  width: number;
  height: number;
  durationMillis: number | null;
  checksum: string;
  deliveryUrl: null;
};

type LobbySectionBase = {
  key: string;
  order: number;
  contentRef: LobbyContentRef;
  assets: LobbyAsset[];
};

export type HeroBannerSection = LobbySectionBase & {
  type: 'HERO_BANNER';
  title: string;
  copy: string;
  tone: LobbyTone;
  presentationVariant: LobbyPresentationVariant;
};

export type AnnouncementStripSection = LobbySectionBase & {
  type: 'ANNOUNCEMENT_STRIP';
  announcements: Array<{
    key: string;
    copy: string;
    tone: LobbyTone;
    order: number;
  }>;
};

export type BossRoomListSection = LobbySectionBase & {
  type: 'BOSS_ROOM_LIST';
  title: string;
  bosses: Array<{
    bossCode: string;
    name: string;
    summary: string;
    difficultyLabel: string;
  }>;
};

export type PlayerSummarySection = LobbySectionBase & {
  type: 'PLAYER_SUMMARY';
  label: string;
};

export type CharacterPreviewSection = LobbySectionBase & {
  type: 'CHARACTER_PREVIEW';
  title: string;
};

export type InventoryPreviewSection = LobbySectionBase & {
  type: 'INVENTORY_PREVIEW';
  title: string;
  emptyCopy: string;
  maxItems: number;
};

export type EventSpotlightSection = LobbySectionBase & {
  type: 'EVENT_SPOTLIGHT';
  title: string;
  copy: string;
  tone: LobbyTone;
};

export type LobbySection =
  | HeroBannerSection
  | AnnouncementStripSection
  | BossRoomListSection
  | PlayerSummarySection
  | CharacterPreviewSection
  | InventoryPreviewSection
  | EventSpotlightSection;

export type LobbyBootstrap = {
  schemaVersion: 1;
  generatedAt: string;
  locale: SupportedLobbyLocale;
  player: {
    displayName: string;
    profileVersion: number;
  };
  navigation: Array<{
    key: string;
    label: string;
    iconKey: LobbyIconKey;
    target: LobbyNavigationTarget;
    order: number;
  }>;
  sections: LobbySection[];
  nextContentBoundaryAt: string | null;
  degraded: {
    active: boolean;
    codes: LobbyDegradedCode[];
  };
};

export type LobbyDegradedCode =
  | 'UNKNOWN_CONTENT_SCHEMA'
  | 'INVALID_CONTENT'
  | 'MISSING_ASSET'
  | 'CONTENT_UNAVAILABLE'
  | 'CLIENT_INVALID_SECTION'
  | 'CLIENT_INVALID_NAVIGATION';

export type ParsedLobbyBootstrap = {
  content: LobbyBootstrap;
  clientDegradedCodes: LobbyDegradedCode[];
};
