import { useCallback, useState } from 'react';
import { navigate } from '../app/AppRoutes';
import { GameHost } from './GameHost';
import type { AnimationProofMetrics } from './types';
import './animation-lab.css';

const initialMetrics: AnimationProofMetrics = {
  fps: 0,
  frameTimeMs: 0,
  renderedObjects: 0,
  renderer: 'WEBGL',
};

export function AnimationLabPage() {
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
  );
  const [metrics, setMetrics] = useState(initialMetrics);
  const updateMetrics = useCallback((next: AnimationProofMetrics) => setMetrics(next), []);

  return (
    <main className="tc-animation-lab">
      <header className="tc-animation-lab__header">
        <div>
          <p className="tc-animation-lab__eyebrow">Phase 5.1 production proof</p>
          <h1>Motion Forge</h1>
        </div>
        <div className="tc-animation-lab__actions">
          <label className="tc-animation-lab__motion-toggle">
            <input
              type="checkbox"
              checked={reducedMotion}
              onChange={(event) => setReducedMotion(event.target.checked)}
            />
            Reduce motion
          </label>
          <button type="button" onClick={() => navigate('/app')}>
            Return to camp
          </button>
        </div>
      </header>

      <section className="tc-animation-lab__stage" aria-describedby="animation-proof-note">
        <GameHost reducedMotion={reducedMotion} onMetrics={updateMetrics} />
        <div className="tc-animation-lab__metrics" aria-label="Animation performance metrics">
          <span><strong>{metrics.fps}</strong> FPS</span>
          <span><strong>{metrics.frameTimeMs}</strong> ms</span>
          <span><strong>{metrics.renderedObjects}</strong> objects</span>
          <span>{metrics.renderer}</span>
        </div>
      </section>

      <footer id="animation-proof-note" className="tc-animation-lab__note">
        Review-sheet motion proof only. Combat timing, damage, hitboxes, rewards and authority
        remain server-owned and are not implemented here.
      </footer>
    </main>
  );
}
