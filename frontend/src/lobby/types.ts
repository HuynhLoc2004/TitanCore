export type LobbyNavigationTarget = 'LOBBY' | 'INVENTORY' | 'SETTINGS';

export type LobbyAsset = {
  assetId: string;
  variantKey: string;
  mediaType: string;
  width: number;
  height: number;
  durationMillis: number | null;
  checksum: string;
  deliveryUrl: string | null;
};

type LobbySectionBase = {
  key: string;
  type: string;
  order: number;
  assets: LobbyAsset[];
};

export type LobbySection =
  | (LobbySectionBase & {
      type: 'HERO_BANNER';
      title: string;
      copy: string;
      tone: string;
      presentationVariant: string;
    })
  | (LobbySectionBase & {
      type: 'ANNOUNCEMENT_STRIP';
      announcements: Array<{ key: string; copy: string; tone: string; order: number }>;
    })
  | (LobbySectionBase & {
      type: 'BOSS_ROOM_LIST';
      title: string;
      bosses: Array<{
        bossCode: string;
        name: string;
        summary: string;
        difficultyLabel: string;
      }>;
    })
  | (LobbySectionBase & {
      type: 'PLAYER_SUMMARY';
      label: string;
    })
  | (LobbySectionBase & {
      type: 'CHARACTER_PREVIEW';
      title: string;
    })
  | (LobbySectionBase & {
      type: 'INVENTORY_PREVIEW';
      title: string;
      emptyCopy: string;
      maxItems: number;
    })
  | (LobbySectionBase & {
      type: 'EVENT_SPOTLIGHT';
      title: string;
      copy: string;
      tone: string;
    });

export type LobbyBootstrap = {
  schemaVersion: number;
  generatedAt: string;
  locale: string;
  player: {
    displayName: string;
    profileVersion: number;
  };
  navigation: Array<{
    key: string;
    label: string;
    iconKey: string;
    target: LobbyNavigationTarget;
    order: number;
  }>;
  sections: LobbySection[];
  nextContentBoundaryAt: string | null;
  degraded: {
    active: boolean;
    codes: string[];
  };
};
