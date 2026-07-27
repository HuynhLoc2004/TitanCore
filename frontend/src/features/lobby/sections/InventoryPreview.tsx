import { Backpack } from 'lucide-react';
import type { InventoryPreviewSection } from '../model/lobbyTypes';

export function InventoryPreview({ section }: { section: InventoryPreviewSection }) {
  return (
    <section className="tc-lobby-inventory" aria-labelledby={`${section.key}-title`}>
      <div className="tc-lobby-section-heading">
        <div>
          <p className="tc-lobby-kicker">Equipment rail</p>
          <h2 id={`${section.key}-title`}>{section.title}</h2>
        </div>
        <Backpack aria-hidden="true" size={23} />
      </div>
      <div className="tc-lobby-inventory-empty">
        <span aria-hidden="true"><Backpack size={28} /></span>
        <p>{section.emptyCopy}</p>
      </div>
    </section>
  );
}
