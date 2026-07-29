import { useCallback, useRef, useState } from 'react';
import { navigate } from '../app/AppRoutes';
import { WorldRuntimeHost } from './WorldRuntimeHost';
import type {
  WorldRuntimeMetrics,
  WorldRuntimeStatus,
} from './world/runtimeTypes';
import './world-runtime.css';
import { UnifiedInputState } from './world/input/UnifiedInputState';
import { MobileControls } from './MobileControls';

const MANIFEST_URL = '/assets/world-proof/raid-camp-world.json';
const INITIAL_STATUS: WorldRuntimeStatus = {
  phase: 'BOOT',
  progress: 0,
  message: 'Preparing local Khu runtime',
};
const INITIAL_METRICS: WorldRuntimeMetrics = {
  fps: 0,
  frameTimeMs: 0,
  simulationHz: 0,
  renderedObjects: 0,
  worldScreens: 0,
  regionCount: 0,
  droppedSimulationMs: 0,
  renderer: 'WEBGL',
  quality: 'BALANCED',
};

export function WorldRuntimePage() {
  const inputState = useRef(new UnifiedInputState()).current;
  const [status, setStatus] = useState(INITIAL_STATUS);
  const [metrics, setMetrics] = useState(INITIAL_METRICS);
  const [retryGeneration, setRetryGeneration] = useState(0);
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false,
  );
  const updateStatus = useCallback((next: WorldRuntimeStatus) => setStatus(next), []);
  const updateMetrics = useCallback((next: WorldRuntimeMetrics) => setMetrics(next), []);
  const retry = () => {
    setStatus(INITIAL_STATUS);
    setMetrics(INITIAL_METRICS);
    setRetryGeneration((generation) => generation + 1);
  };

  return (
    <main className="tc-world-runtime">
      <header className="tc-world-runtime__header">
        <div>
          <p className="tc-world-runtime__eyebrow">Phase 5.3B enemy behavior sandbox</p>
          <h1>Raid Camp Local Khu</h1>
        </div>
        <div className="tc-world-runtime__actions">
          <label>
            <input
              type="checkbox"
              checked={reducedMotion}
              onChange={(event) => setReducedMotion(event.target.checked)}
            />
            Reduce motion
          </label>
          {status.phase === 'FAILED' && (
            <button type="button" onClick={retry}>Retry</button>
          )}
          <button type="button" onClick={() => navigate('/app')}>Return to camp</button>
        </div>
      </header>

      <section className="tc-world-runtime__stage" aria-describedby="world-runtime-scope">
        <WorldRuntimeHost
          manifestUrl={MANIFEST_URL}
          reducedMotion={reducedMotion}
          inputState={inputState}
          retryGeneration={retryGeneration}
          onStatus={updateStatus}
          onMetrics={updateMetrics}
        />
        <MobileControls input={inputState} disabled={status.phase !== 'READY'} />
        {(status.phase === 'BOOT' || status.phase === 'LOADING') && (
          <div className="tc-world-runtime__loading" role="status" aria-live="polite">
            <span className="tc-world-runtime__loader" aria-hidden="true" />
            <strong>{status.message}</strong>
            <span>{status.progress}%</span>
          </div>
        )}
        <div className="tc-world-runtime__metrics" aria-label="World runtime metrics">
          <span><strong>{metrics.fps}</strong> FPS</span>
          <span><strong>{metrics.simulationHz}</strong> Hz sim</span>
          <span><strong>{metrics.frameTimeMs}</strong> ms</span>
          <span><strong>{metrics.renderedObjects}</strong> objects</span>
          <span><strong>{metrics.worldScreens}</strong> screens</span>
          <span><strong>{metrics.regionCount}</strong> regions</span>
          <span>{metrics.quality}</span>
          <span>{metrics.renderer}</span>
        </div>
        <div className="tc-world-runtime__status" data-phase={status.phase}>
          {status.phase === 'SLEEPING' ? 'Runtime sleeping' : status.message}
        </div>
      </section>

      <footer id="world-runtime-scope" className="tc-world-runtime__note">
        Local enemy-behavior proof only. Damage, loot and multiplayer authority are not
        server-backed.
      </footer>
    </main>
  );
}
