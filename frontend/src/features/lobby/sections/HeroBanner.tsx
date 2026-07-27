import { Flag, Sparkles } from 'lucide-react';
import type { HeroBannerSection } from '../model/lobbyTypes';

export function HeroBanner({ section }: { section: HeroBannerSection }) {
  return (
    <section
      className="tc-lobby-hero"
      data-tone={section.tone}
      aria-labelledby={`${section.key}-title`}
    >
      <div className="tc-lobby-hero-copy">
        <p className="tc-lobby-kicker"><Flag size={16} aria-hidden="true" /> Published signal</p>
        <h2 id={`${section.key}-title`}>{section.title}</h2>
        <p>{section.copy}</p>
      </div>
      <div className="tc-lobby-hero-emblem" aria-hidden="true">
        <Sparkles size={42} />
        <span />
      </div>
    </section>
  );
}
