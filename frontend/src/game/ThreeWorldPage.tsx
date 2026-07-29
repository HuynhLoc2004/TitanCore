import { useCallback, useState } from 'react';
import { navigate } from '../app/AppRoutes';
import { ThreeWorldHost } from './ThreeWorldHost';
import type {
  ThreeWorldMetrics,
  ThreeWorldStatus,
} from './three/runtimeTypes';
import './three-world.css';

const INITIAL_STATUS: ThreeWorldStatus = {
  phase: 'BOOT',
  message: 'Building stylized 3D world',
};

const INITIAL_METRICS: ThreeWorldMetrics = {
  fps: 0,
  frameTimeMs: 0,
  drawCalls: 0,
  triangles: 0,
  geometries: 0,
  textures: 0,
  quality: 'BALANCED',
};

export function ThreeWorldPage() {
  const [status, setStatus] = useState(INITIAL_STATUS);
  const [metrics, setMetrics] = useState(INITIAL_METRICS);
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
  );
  const onStatus = useCallback((next: ThreeWorldStatus) => setStatus(next), []);
  const onMetrics = useCallback((next: ThreeWorldMetrics) => setMetrics(next), []);

  return (
    <main className="tc-three-world">
      <ThreeWorldHost
        reducedMotion={reducedMotion}
        onStatus={onStatus}
        onMetrics={onMetrics}
      />

      <header className="tc-three-world__hud">
        <div className="tc-three-world__identity">
          <span className="tc-three-world__core" aria-hidden="true" />
          <div>
            <p>Three.js world proof</p>
            <h1>Zephyr Frontier</h1>
          </div>
        </div>
        <div className="tc-three-world__actions">
          <label>
            <input
              type="checkbox"
              checked={reducedMotion}
              onChange={(event) => setReducedMotion(event.target.checked)}
            />
            Reduce motion
          </label>
          <button type="button" onClick={() => navigate('/app')}>Exit</button>
        </div>
      </header>

      <aside className="tc-three-world__guide" aria-label="World controls">
        <strong>Explore</strong>
        <span><kbd>WASD</kbd> move</span>
        <span><kbd>Drag</kbd> orbit 360°</span>
        <span><kbd>Wheel</kbd> zoom</span>
        <span><kbd>Space</kbd> jump / hold to glide</span>
      </aside>

      <section className="tc-three-world__metrics" aria-label="3D renderer metrics">
        <span><strong>{metrics.fps}</strong> fps</span>
        <span>{metrics.frameTimeMs} ms</span>
        <span>{metrics.drawCalls} calls</span>
        <span>{metrics.triangles.toLocaleString()} tris</span>
        <span>{metrics.geometries} geo</span>
        <span>{metrics.quality}</span>
      </section>

      {status.phase === 'BOOT' && (
        <div className="tc-three-world__loading" role="status" aria-live="polite">
          <span aria-hidden="true" />
          <strong>{status.message}</strong>
        </div>
      )}

      <div className="tc-three-world__prototype-note">
        Camera and renderer proof. Hero geometry is not production character art.
      </div>
    </main>
  );
}
