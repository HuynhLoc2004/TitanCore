import { useEffect, useMemo, useState } from 'react';
import type { User } from '../auth/api';
import { fetchLobbyBootstrap } from './api';
import type { LobbyAsset, LobbyBootstrap, LobbySection } from './types';
import './lobby.css';

type LobbyPageProps = {
  user: User;
  onLogout: () => Promise<void>;
};

type LoadState =
  | { status: 'loading' }
  | { status: 'ready'; data: LobbyBootstrap }
  | { status: 'error'; message: string };

const allowedAssetProtocols = new Set(['https:', 'http:']);

export function LobbyPage({ user, onLogout }: LobbyPageProps) {
  const [loadState, setLoadState] = useState<LoadState>({ status: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    setLoadState({ status: 'loading' });
    fetchLobbyBootstrap(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setLoadState({ status: 'ready', data });
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setLoadState({
            status: 'error',
            message: 'The camp signal dropped. Your raid pass is still safe.',
          });
        }
      });
    return () => controller.abort();
  }, []);

  const displayName = loadState.status === 'ready'
    ? loadState.data.player.displayName
    : user.profile.displayName;

  return (
    <main className="tc-raid-camp">
      <div className="tc-raid-camp__scene" aria-hidden="true" />
      <a className="tc-skip-link" href="#raid-camp-content">Skip to camp content</a>

      <header className="tc-camp-header">
        <a className="tc-camp-brand" href="/app" aria-label="TitanCore Raid Camp">
          <span className="tc-camp-brand__mark" aria-hidden="true">T</span>
          <span>
            <strong>TitanCore</strong>
            <small>Raid Camp</small>
          </span>
        </a>

        <div className="tc-camp-identity">
          <span className="tc-camp-identity__avatar" aria-hidden="true">
            {(displayName ?? 'T').slice(0, 1).toLocaleUpperCase()}
          </span>
          <span className="tc-camp-identity__copy">
            <small>Raider</small>
            <strong>{displayName}</strong>
          </span>
          <button className="tc-camp-logout" type="button" onClick={() => void onLogout()}>
            Log out
          </button>
        </div>
      </header>

      <div className="tc-camp-layout">
        <CampNavigation data={loadState.status === 'ready' ? loadState.data : null} />

        <div className="tc-camp-content" id="raid-camp-content">
          <CampHero name={displayName} state={loadState} />
          <CampSections state={loadState} />
        </div>
      </div>
    </main>
  );
}

function CampNavigation({ data }: { data: LobbyBootstrap | null }) {
  const navigation = data?.navigation ?? [];
  return (
    <nav className="tc-camp-nav" aria-label="Camp navigation">
      <a className="tc-camp-nav__item is-active" href="/app" aria-current="page">
        <span aria-hidden="true">01</span>
        <strong>Camp</strong>
      </a>
      {navigation
        .filter((item) => item.target !== 'LOBBY')
        .sort((left, right) => left.order - right.order)
        .map((item, index) => (
          <button className="tc-camp-nav__item" key={item.key} type="button" disabled>
            <span aria-hidden="true">{String(index + 2).padStart(2, '0')}</span>
            <strong>{item.label}</strong>
          </button>
        ))}
    </nav>
  );
}

function CampHero({ name, state }: { name: string | null; state: LoadState }) {
  const hero = state.status === 'ready'
    ? state.data.sections.find((section) => section.type === 'HERO_BANNER')
    : undefined;
  const heroAsset = hero ? preferredImage(hero.assets) : null;

  return (
    <section className="tc-camp-hero" aria-labelledby="camp-heading">
      <div className="tc-camp-hero__copy">
        <p className="tc-camp-kicker">The portal is awake</p>
        <h1 id="camp-heading">{hero?.type === 'HERO_BANNER' ? hero.title : `Ready, ${name ?? 'Raider'}?`}</h1>
        <p>
          {hero?.type === 'HERO_BANNER'
            ? hero.copy
            : 'Gather at the fire. Fresh raid signals will appear here as soon as command clears them.'}
        </p>
        <div className="tc-camp-hero__status" role="status">
          <span aria-hidden="true" />
          {state.status === 'loading' && 'Syncing camp signals'}
          {state.status === 'error' && 'Camp signal unavailable'}
          {state.status === 'ready' && (state.data.degraded.active ? 'Limited camp signal' : 'Camp signal steady')}
        </div>
      </div>
      <div className="tc-camp-hero__stage" aria-hidden="true">
        <img
          className="tc-camp-hero__raider"
          src={heroAsset ?? '/assets/raid-camp/core-raider.png'}
          alt=""
          width="560"
          height="760"
          loading="eager"
        />
      </div>
    </section>
  );
}

function CampSections({ state }: { state: LoadState }) {
  if (state.status === 'loading') {
    return <CampSkeleton />;
  }
  if (state.status === 'error') {
    return (
      <section className="tc-camp-message" role="alert">
        <p className="tc-camp-kicker">Signal interrupted</p>
        <h2>Camp dispatch is taking a breather</h2>
        <p>{state.message}</p>
        <button type="button" onClick={() => window.location.reload()}>Try again</button>
      </section>
    );
  }

  const sections = [...state.data.sections]
    .filter((section) => section.type !== 'HERO_BANNER')
    .sort((left, right) => left.order - right.order);

  if (sections.length === 0) {
    return (
      <section className="tc-camp-message">
        <p className="tc-camp-kicker">Quiet watch</p>
        <h2>No raid dispatches yet</h2>
        <p>The camp is ready. Approved rooms, events and supplies will arrive here without an app update.</p>
      </section>
    );
  }

  return (
    <div className="tc-camp-sections">
      {sections.map((section) => <DynamicSection key={section.key} section={section} />)}
    </div>
  );
}

function DynamicSection({ section }: { section: LobbySection }) {
  switch (section.type) {
    case 'ANNOUNCEMENT_STRIP':
      return (
        <section className="tc-camp-announcements" aria-label="Camp announcements">
          {section.announcements
            .sort((left, right) => left.order - right.order)
            .map((announcement) => <p key={announcement.key}>{announcement.copy}</p>)}
        </section>
      );
    case 'BOSS_ROOM_LIST':
      {
        const bossArt = preferredImage(section.assets);
      return (
        <section className="tc-camp-band" aria-labelledby={`${section.key}-heading`}>
          <BandHeading eyebrow="Raid board" id={`${section.key}-heading`} title={section.title} />
          {section.bosses.length > 0 ? (
            <div className="tc-boss-grid">
              {section.bosses.map((boss, index) => (
                <article className="tc-boss-card" key={boss.bossCode}>
                  <div className="tc-boss-card__art">
                    {index === 0 && bossArt && <img src={bossArt} alt="" loading="lazy" />}
                  </div>
                  <div>
                    <span>{boss.difficultyLabel}</span>
                    <h3>{boss.name}</h3>
                    <p>{boss.summary}</p>
                    <button type="button" disabled>Room discovery coming next</button>
                  </div>
                </article>
              ))}
            </div>
          ) : <InlineEmpty copy="No approved boss rooms are broadcasting right now." />}
        </section>
      );
      }
    case 'CHARACTER_PREVIEW':
      return (
        <section className="tc-camp-band tc-camp-band--compact">
          <BandHeading eyebrow="Your raider" title={section.title} />
          <InlineEmpty copy="Character loadout arrives with the approved inventory contract." />
        </section>
      );
    case 'INVENTORY_PREVIEW':
      return (
        <section className="tc-camp-band tc-camp-band--compact">
          <BandHeading eyebrow="Field pack" title={section.title} />
          <InlineEmpty copy={section.emptyCopy} />
        </section>
      );
    case 'EVENT_SPOTLIGHT': {
      const asset = preferredImage(section.assets);
      return (
        <section className="tc-event-spotlight">
          {asset && <img src={asset} alt="" loading="lazy" />}
          <div>
            <p className="tc-camp-kicker">Camp event</p>
            <h2>{section.title}</h2>
            <p>{section.copy}</p>
          </div>
        </section>
      );
    }
    case 'PLAYER_SUMMARY':
      return (
        <section className="tc-player-summary">
          <span aria-hidden="true">Core</span>
          <strong>{section.label}</strong>
        </section>
      );
    default:
      return null;
  }
}

function BandHeading({ eyebrow, title, id }: { eyebrow: string; title: string; id?: string }) {
  return (
    <header className="tc-camp-band__heading">
      <p className="tc-camp-kicker">{eyebrow}</p>
      <h2 id={id}>{title}</h2>
    </header>
  );
}

function InlineEmpty({ copy }: { copy: string }) {
  return <p className="tc-camp-empty">{copy}</p>;
}

function CampSkeleton() {
  return (
    <section className="tc-camp-skeleton" aria-label="Loading camp dispatches" aria-busy="true">
      <span />
      <span />
      <span />
    </section>
  );
}

function preferredImage(assets: LobbyAsset[]) {
  const candidate = assets.find((asset) => safeAssetUrl(asset.deliveryUrl));
  return candidate?.deliveryUrl ?? null;
}

function safeAssetUrl(value: string | null) {
  if (!value) {
    return false;
  }
  try {
    const url = new URL(value, window.location.origin);
    return allowedAssetProtocols.has(url.protocol);
  } catch {
    return false;
  }
}
