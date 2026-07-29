import { useEffect, useRef, useState } from 'react';
import type {
  ThreeWorldMetrics,
  ThreeWorldRuntimeHandle,
  ThreeWorldStatus,
} from './three/runtimeTypes';

type ThreeWorldHostProps = {
  reducedMotion: boolean;
  onStatus: (status: ThreeWorldStatus) => void;
  onMetrics: (metrics: ThreeWorldMetrics) => void;
  loadRenderer?: () => Promise<
    typeof import('./three/createThreeWorldRuntime').createThreeWorldRuntime
  >;
};

const loadThreeWorldRuntime = () => import('./three/createThreeWorldRuntime')
  .then(({ createThreeWorldRuntime }) => createThreeWorldRuntime);

export function ThreeWorldHost({
  reducedMotion,
  onStatus,
  onMetrics,
  loadRenderer = loadThreeWorldRuntime,
}: ThreeWorldHostProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const runtimeRef = useRef<ThreeWorldRuntimeHandle | null>(null);
  const statusRef = useRef(onStatus);
  const metricsRef = useRef(onMetrics);
  const [failed, setFailed] = useState(false);
  statusRef.current = onStatus;
  metricsRef.current = onMetrics;

  useEffect(() => {
    const parent = parentRef.current;
    if (!parent) return undefined;
    let disposed = false;
    setFailed(false);
    statusRef.current({ phase: 'BOOT', message: 'Building stylized 3D world' });
    void loadRenderer()
      .then((createThreeWorldRuntime) => {
        if (disposed) return;
        runtimeRef.current = createThreeWorldRuntime({
          parent,
          reducedMotion,
          onStatus: (status) => !disposed && statusRef.current(status),
          onMetrics: (metrics) => !disposed && metricsRef.current(metrics),
        });
      })
      .catch(() => {
        if (!disposed) {
          setFailed(true);
          statusRef.current({ phase: 'FAILED', message: '3D renderer could not start' });
        }
      });

    return () => {
      disposed = true;
      runtimeRef.current?.destroy();
      runtimeRef.current = null;
      parent.replaceChildren();
    };
  }, [loadRenderer]);

  useEffect(() => {
    runtimeRef.current?.setReducedMotion(reducedMotion);
  }, [reducedMotion]);

  return (
    <div className="tc-three-world__host">
      <div ref={parentRef} className="tc-three-world__canvas" />
      {failed && (
        <div className="tc-three-world__failure" role="alert">
          This browser could not prepare the 3D world.
        </div>
      )}
    </div>
  );
}
