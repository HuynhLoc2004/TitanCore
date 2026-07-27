import { LogOut, Settings, Sparkles } from 'lucide-react';

export function LobbyHeader({
  displayName,
  onLogout,
}: {
  displayName: string;
  onLogout: () => void;
}) {
  return (
    <header className="tc-lobby-header">
      <a className="tc-lobby-brand" href="#raid-board" aria-label="TitanCore raid lobby">
        <span className="tc-lobby-brand-mark" aria-hidden="true">
          <Sparkles size={22} strokeWidth={2.5} />
        </span>
        <span>
          <small>TitanCore</small>
          <strong>Raid camp</strong>
        </span>
      </a>
      <div className="tc-lobby-identity">
        <span className="tc-lobby-avatar" aria-hidden="true">
          {displayName.slice(0, 1).toLocaleUpperCase()}
        </span>
        <span className="tc-lobby-player-name">
          <small>Raid banner</small>
          <strong>{displayName}</strong>
        </span>
      </div>
      <div className="tc-lobby-header-actions">
        <button
          className="tc-lobby-icon-button"
          type="button"
          disabled
          title="Settings are not available in this release"
          aria-label="Settings, coming later"
        >
          <Settings aria-hidden="true" size={21} />
        </button>
        <button
          className="tc-lobby-icon-button tc-lobby-icon-button-danger"
          type="button"
          title="Log out"
          aria-label="Log out"
          onClick={onLogout}
        >
          <LogOut aria-hidden="true" size={21} />
        </button>
      </div>
    </header>
  );
}
