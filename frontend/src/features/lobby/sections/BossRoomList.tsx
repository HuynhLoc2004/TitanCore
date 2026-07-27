import { LockKeyhole, Swords } from 'lucide-react';
import type { BossRoomListSection } from '../model/lobbyTypes';

export function BossRoomList({ section }: { section: BossRoomListSection }) {
  return (
    <section className="tc-lobby-raid-board" id="raid-board" aria-labelledby={`${section.key}-title`}>
      <div className="tc-lobby-section-heading">
        <div>
          <p className="tc-lobby-kicker"><Swords size={17} aria-hidden="true" /> Raid board</p>
          <h2 id={`${section.key}-title`}>{section.title}</h2>
        </div>
        <span>{section.bosses.length} published</span>
      </div>
      {section.bosses.length === 0 ? (
        <div className="tc-lobby-inline-empty">No boss presentations are published.</div>
      ) : (
        <div className="tc-lobby-boss-list">
          {section.bosses.map((boss, index) => (
            <article className="tc-lobby-boss-card" key={boss.bossCode} tabIndex={0}>
              <div className="tc-lobby-boss-silhouette" aria-hidden="true">
                <span>{String(index + 1).padStart(2, '0')}</span>
              </div>
              <div className="tc-lobby-boss-copy">
                <span className="tc-lobby-difficulty">{boss.difficultyLabel}</span>
                <h3>{boss.name}</h3>
                <p>{boss.summary}</p>
                <span className="tc-lobby-entry-unavailable">
                  <LockKeyhole size={15} aria-hidden="true" />
                  Live raid entry is not available yet
                </span>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
