import { ShieldCheck } from 'lucide-react';
import type { PlayerSummarySection } from '../model/lobbyTypes';

export function PlayerSummary({
  section,
  displayName,
}: {
  section: PlayerSummarySection;
  displayName: string;
}) {
  return (
    <section className="tc-lobby-player-summary" aria-label={section.label}>
      <ShieldCheck aria-hidden="true" size={24} />
      <span>
        <small>{section.label}</small>
        <strong>{displayName}</strong>
      </span>
    </section>
  );
}
