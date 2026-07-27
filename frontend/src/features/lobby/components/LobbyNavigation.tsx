import { Backpack, Home, Settings, type LucideIcon } from 'lucide-react';
import type {
  LobbyIconKey,
  LobbyNavigationTarget,
  LobbyBootstrap,
} from '../model/lobbyTypes';

const icons: Record<LobbyIconKey, LucideIcon> = {
  HOME: Home,
  BACKPACK: Backpack,
  SETTINGS: Settings,
};

const capabilities: Record<LobbyNavigationTarget, { available: boolean }> = {
  LOBBY: { available: true },
  INVENTORY: { available: false },
  SETTINGS: { available: false },
};

export function LobbyNavigation({
  items,
}: {
  items: LobbyBootstrap['navigation'];
}) {
  if (items.length === 0) {
    return null;
  }
  return (
    <nav className="tc-lobby-navigation" aria-label="Raid camp navigation">
      {items.map((item) => {
        const Icon = icons[item.iconKey];
        const available = capabilities[item.target].available;
        return (
          <button
            className="tc-lobby-nav-item"
            data-active={item.target === 'LOBBY'}
            type="button"
            key={item.key}
            disabled={!available}
            title={available ? item.label : `${item.label}, coming later`}
            aria-current={item.target === 'LOBBY' ? 'page' : undefined}
            aria-label={available ? item.label : `${item.label}, coming later`}
          >
            <Icon aria-hidden="true" size={20} />
            <span>{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
