import { Megaphone } from 'lucide-react';
import type { AnnouncementStripSection } from '../model/lobbyTypes';

export function AnnouncementStrip({ section }: { section: AnnouncementStripSection }) {
  if (section.announcements.length === 0) {
    return null;
  }
  return (
    <section className="tc-lobby-announcements" aria-label="Camp announcements">
      <Megaphone aria-hidden="true" size={19} />
      <ul>
        {section.announcements.map((announcement) => (
          <li key={announcement.key} data-tone={announcement.tone}>{announcement.copy}</li>
        ))}
      </ul>
    </section>
  );
}
