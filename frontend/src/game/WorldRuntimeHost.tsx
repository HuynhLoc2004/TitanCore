import { useEffect, useRef, useState } from 'react';
import type { createWorldRuntime } from './world/createWorldRuntime';
import type {
  WorldRuntimeHandle,
  WorldRuntimeMetrics,
  WorldRuntimeStatus,
} from './world/runtimeTypes';
import type { UnifiedInputState } from './world/input/UnifiedInputState';

type WorldRuntimeFactory = typeof createWorldRuntime;

type WorldRuntimeHostProps = {
  manifestUrl: string;
  reducedMotion: boolean;
  inputState: UnifiedInputState;
  retryGeneration: number;
  onStatus: (status: WorldRuntimeStatus) => void;
  onMetrics: (metrics: WorldRuntimeMetrics) => void;
  loadRenderer?: () => Promise<WorldRuntimeFactory>;
};

const STARTUP_TIMEOUT_MS = 20_000;
const loadWorldRuntime: NonNullable<WorldRuntimeHostProps['loadRenderer']> = () =>
  import('./world/createWorldRuntime').then(({ createWorldRuntime: create }) => create);

export function WorldRuntimeHost({
  manifestUrl,
  reducedMotion,
  inputState,
  retryGeneration,
  onStatus,
  onMetrics,
  loadRenderer = loadWorldRuntime,
}: WorldRuntimeHostProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<WorldRuntimeHandle | null>(null);
  const generationRef = useRef(0);
  const statusRef = useRef(onStatus);
  const metricsRef = useRef(onMetrics);
  const [failed, setFailed] = useState(false);

  statusRef.current = onStatus;
  metricsRef.current = onMetrics;

  useEffect(() => {
    const parent = parentRef.current;
    if (!parent) {
      return undefined;
    }
    setFailed(false);
    const generation = ++generationRef.current;
    let disposed = false;
    let startupTimer: number | undefined;

    const failStartup = () => {
      if (!disposed && generation === generationRef.current) {
        window.clearTimeout(startupTimer);
        handleRef.current?.destroy();
        handleRef.current = null;
        statusRef.current({
          phase: 'FAILED',
          progress: 0,
          message: 'World runtime could not start',
        });
        setFailed(true);
      }
    };

    void loadRenderer()
      .then((create) => {
        if (disposed || generation !== generationRef.current) {
          return;
        }
        startupTimer = window.setTimeout(failStartup, STARTUP_TIMEOUT_MS);
        handleRef.current = create({
          parent,
          manifestUrl,
          reducedMotion,
          inputState,
          onStatus: (status) => {
            if (!disposed && generation === generationRef.current) {
              statusRef.current(status);
            }
          },
          onMetrics: (metrics) => {
            if (!disposed && generation === generationRef.current) {
              metricsRef.current(metrics);
            }
          },
          onReady: () => window.clearTimeout(startupTimer),
          onFailure: failStartup,
        });
      })
      .catch(failStartup);

    return () => {
      disposed = true;
      inputState.releaseAll();
      window.clearTimeout(startupTimer);
      generationRef.current += 1;
      handleRef.current?.destroy();
      handleRef.current = null;
      parent.replaceChildren();
    };
  }, [inputState, loadRenderer, manifestUrl, retryGeneration]);

  useEffect(() => {
    handleRef.current?.setReducedMotion(reducedMotion);
  }, [reducedMotion]);

  return (
    <div className="tc-world-runtime__host">
      <div
        ref={parentRef}
        className="tc-world-runtime__canvas"
        aria-label="TitanCore local Khu runtime"
      />
      {failed && (
        <div className="tc-world-runtime__failure" role="alert">
          The local Khu could not be prepared. Retry or return to camp.
        </div>
      )}
    </div>
  );
}
