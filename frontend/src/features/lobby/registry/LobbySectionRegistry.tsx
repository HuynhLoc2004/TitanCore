import type { LobbySection } from '../model/lobbyTypes';
import { AnnouncementStrip } from '../sections/AnnouncementStrip';
import { BossRoomList } from '../sections/BossRoomList';
import { CharacterPreview } from '../sections/CharacterPreview';
import { EventSpotlight } from '../sections/EventSpotlight';
import { HeroBanner } from '../sections/HeroBanner';
import { InventoryPreview } from '../sections/InventoryPreview';
import { PlayerSummary } from '../sections/PlayerSummary';

export function LobbySectionRegistry({
  sections,
  displayName,
}: {
  sections: LobbySection[];
  displayName: string;
}) {
  return sections.map((section) => {
    switch (section.type) {
      case 'HERO_BANNER':
        return <HeroBanner section={section} key={section.key} />;
      case 'ANNOUNCEMENT_STRIP':
        return <AnnouncementStrip section={section} key={section.key} />;
      case 'BOSS_ROOM_LIST':
        return <BossRoomList section={section} key={section.key} />;
      case 'PLAYER_SUMMARY':
        return <PlayerSummary section={section} displayName={displayName} key={section.key} />;
      case 'CHARACTER_PREVIEW':
        return <CharacterPreview section={section} key={section.key} />;
      case 'INVENTORY_PREVIEW':
        return <InventoryPreview section={section} key={section.key} />;
      case 'EVENT_SPOTLIGHT':
        return <EventSpotlight section={section} key={section.key} />;
    }
  });
}
