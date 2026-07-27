import { AlertTriangle, Swords } from 'lucide-react';
import { useMemo, useRef } from 'react';
import type { User } from '../../../auth/api';
import { useAuth } from '../../../auth/useAuth';
import { resolveLobbyLocale } from '../api/lobbyApi';
import { LobbyHeader } from '../components/LobbyHeader';
import { LobbyNavigation } from '../components/LobbyNavigation';
import { LobbyStatePanel } from '../components/LobbyStatePanel';
import { useLobbyBootstrap } from '../hooks/useLobbyBootstrap';
import type { LobbyState } from '../model/lobbyState';
import type { LobbySection } from '../model/lobbyTypes';
import {
  useLobbyParallax,
  useLobbyQuality,
  type LobbyQuality,
} from '../motion/useLobbyQuality';
import { LobbySectionRegistry } from '../registry/LobbySectionRegistry';
import '../lobby.css';

export default function LobbyPage() {
  const { user, logout } = useAuth();
  const locale = useMemo(() => resolveLobbyLocale(), []);
  const { state, retry } = useLobbyBootstrap(user?.id ?? '', locale);
  const quality = useLobbyQuality();

  if (!user || user.profile.onboardingStatus !== 'COMPLETED') {
    return null;
  }

  return (
    <LobbyView
      user={user}
      state={state}
      quality={quality}
      onRetry={retry}
      onLogout={() => void logout()}
    />
  );
}

export function LobbyView({
  user,
  state,
  quality,
  onRetry,
  onLogout,
}: {
  user: User;
  state: LobbyState;
  quality: LobbyQuality;
  onRetry: () => void;
  onLogout: () => void;
}) {
  const root = useRef<HTMLElement>(null);
  useLobbyParallax(root, quality === 'STANDARD');

  const content = state.content;
  const displayName = content?.player.displayName ?? user.profile.displayName ?? '';
  const sections = content?.sections ?? [];
  const announcements = sectionsOf(sections, ['ANNOUNCEMENT_STRIP']);
  const heroes = sectionsOf(sections, ['HERO_BANNER']);
  const raids = sectionsOf(sections, ['BOSS_ROOM_LIST']);
  const secondary = sectionsOf(sections, [
    'PLAYER_SUMMARY',
    'CHARACTER_PREVIEW',
    'INVENTORY_PREVIEW',
  ]);
  const events = sectionsOf(sections, ['EVENT_SPOTLIGHT']);
  const showContent = content !== null && state.status !== 'EMPTY';

  return (
    <main className="tc-lobby" data-quality={quality} ref={root}>
      <div className="tc-lobby-depth tc-lobby-depth-grid" aria-hidden="true" />
      <div className="tc-lobby-depth tc-lobby-depth-energy" aria-hidden="true" />
      <div className="tc-lobby-depth tc-lobby-depth-particles" aria-hidden="true">
        {Array.from({ length: 8 }, (_, index) => <i key={index} />)}
      </div>

      <div className="tc-lobby-frame">
        <LobbyHeader displayName={displayName} onLogout={onLogout} />
        {content && <LobbyNavigation items={content.navigation} />}
        {content && (
          <LobbySectionRegistry sections={announcements} displayName={displayName} />
        )}
        <section className="tc-lobby-mission-heading" aria-labelledby="tc-lobby-primary-heading">
          <p className="tc-lobby-kicker"><Swords aria-hidden="true" size={18} /> Primary mission</p>
          <h1 id="tc-lobby-primary-heading">Choose a boss raid</h1>
          <p>Read the published raid board, inspect the threat, and prepare your banner.</p>
        </section>

        {state.status === 'BOOTSTRAPPING' && <LobbyStatePanel status="BOOTSTRAPPING" />}
        {state.status === 'EMPTY' && <LobbyStatePanel status="EMPTY" />}
        {state.status === 'FATAL_ERROR' && <LobbyStatePanel status="FATAL_ERROR" onRetry={onRetry} />}
        {state.status === 'AUTH_REQUIRED' && <LobbyStatePanel status="AUTH_REQUIRED" />}
        {state.status === 'ONBOARDING_REQUIRED' && <LobbyStatePanel status="ONBOARDING_REQUIRED" />}
        {state.status === 'OFFLINE' && <LobbyStatePanel status="OFFLINE" onRetry={onRetry} />}
        {state.status === 'RETRYING' && <LobbyStatePanel status="RETRYING" />}
        {state.status === 'DEGRADED' && (
          <section className="tc-lobby-degraded" role="status" aria-live="polite">
            <AlertTriangle aria-hidden="true" size={20} />
            <p>Some published lobby content is temporarily unavailable. Safe sections remain visible.</p>
          </section>
        )}

        {showContent && (
          <div className="tc-lobby-content">
            <LobbySectionRegistry sections={heroes} displayName={displayName} />
            <div className="tc-lobby-two-rail">
              <div className="tc-lobby-primary-rail">
                {raids.length > 0 ? (
                  <LobbySectionRegistry sections={raids} displayName={displayName} />
                ) : (
                  <section className="tc-lobby-raid-board" id="raid-board" aria-label="Raid board">
                    <div className="tc-lobby-inline-empty">
                      No boss raid presentations are currently published.
                    </div>
                  </section>
                )}
              </div>
              <aside className="tc-lobby-secondary-rail" aria-label="Player preparation">
                <LobbySectionRegistry sections={secondary} displayName={displayName} />
              </aside>
            </div>
            <LobbySectionRegistry sections={events} displayName={displayName} />
          </div>
        )}
      </div>
    </main>
  );
}

function sectionsOf<T extends LobbySection['type']>(
  sections: LobbySection[],
  types: T[],
) {
  const accepted = new Set<LobbySection['type']>(types);
  return sections.filter((section) => accepted.has(section.type));
}
