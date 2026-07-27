import { Shield } from 'lucide-react';
import type { CharacterPreviewSection } from '../model/lobbyTypes';

export function CharacterPreview({ section }: { section: CharacterPreviewSection }) {
  return (
    <section className="tc-lobby-character" aria-labelledby={`${section.key}-title`}>
      <div className="tc-lobby-section-heading">
        <div>
          <p className="tc-lobby-kicker">Banner preview</p>
          <h2 id={`${section.key}-title`}>{section.title}</h2>
        </div>
      </div>
      <div className="tc-lobby-character-stage" aria-label="Abstract character preview">
        <span className="tc-lobby-character-head" aria-hidden="true" />
        <span className="tc-lobby-character-body" aria-hidden="true" />
        <span className="tc-lobby-character-shield" aria-hidden="true">
          <Shield size={44} />
        </span>
      </div>
      <p className="tc-lobby-preview-note">Final character art arrives through the reviewed asset pipeline.</p>
    </section>
  );
}
