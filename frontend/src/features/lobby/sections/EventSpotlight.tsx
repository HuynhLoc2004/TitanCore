import { CalendarDays } from 'lucide-react';
import type { EventSpotlightSection } from '../model/lobbyTypes';

export function EventSpotlight({ section }: { section: EventSpotlightSection }) {
  return (
    <section
      className="tc-lobby-event"
      data-tone={section.tone}
      aria-labelledby={`${section.key}-title`}
    >
      <CalendarDays aria-hidden="true" size={24} />
      <div>
        <p className="tc-lobby-kicker">Event spotlight</p>
        <h2 id={`${section.key}-title`}>{section.title}</h2>
        <p>{section.copy}</p>
      </div>
    </section>
  );
}
