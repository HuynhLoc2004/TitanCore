import { useEffect, useRef, useState } from 'react';
import type { AnimationProofHandle, AnimationProofMetrics } from './types';

type AnimationProofFactory = typeof import('./phaser/createAnimationProof')['createAnimationProof'];

type GameHostProps = {
  reducedMotion: boolean;
  onMetrics: (metrics: AnimationProofMetrics) => void;
  loadRenderer?: () => Promise<AnimationProofFactory>;
};

const STARTUP_TIMEOUT_MS = 15_000;
const loadAnimationProof: NonNullable<GameHostProps['loadRenderer']> = () =>
  import('./phaser/createAnimationProof').then(({ createAnimationProof }) => createAnimationProof);

export function GameHost({
  reducedMotion,
  onMetrics,
  loadRenderer = loadAnimationProof,
}: GameHostProps) {
  const parentRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<AnimationProofHandle | null>(null);
  const generationRef = useRef(0);
  const metricsRef = useRef(onMetrics);
  const [error, setError] = useState(false);

  metricsRef.current = onMetrics;

  useEffect(() => {
    const parent = parentRef.current;
    if (!parent) {
      return undefined;
    }

    const generation = ++generationRef.current;
    let disposed = false;
    let startupTimer: number | undefined;

    const failStartup = () => {
      if (!disposed && generation === generationRef.current) {
        handleRef.current?.destroy();
        handleRef.current = null;
        setError(true);
      }
    };

    void loadRenderer()
      .then((createAnimationProof) => {
        if (disposed || generation !== generationRef.current) {
          return;
        }
        startupTimer = window.setTimeout(failStartup, STARTUP_TIMEOUT_MS);
        handleRef.current = createAnimationProof({
          parent,
          reducedMotion,
          onMetrics: (metrics) => {
            if (!disposed && generation === generationRef.current) {
              metricsRef.current(metrics);
            }
          },
          onReady: () => {
            window.clearTimeout(startupTimer);
          },
          onFailure: failStartup,
        });
      })
      .catch(failStartup);

    return () => {
      disposed = true;
      window.clearTimeout(startupTimer);
      generationRef.current += 1;
      handleRef.current?.destroy();
      handleRef.current = null;
      parent.replaceChildren();
    };
  }, [loadRenderer]);

  useEffect(() => {
    handleRef.current?.setReducedMotion(reducedMotion);
  }, [reducedMotion]);

  if (error) {
    return (
      <div className="tc-animation-lab__error" role="alert">
        The animation renderer could not start. Return to camp and try again.
      </div>
    );
  }

  return (
    <div
      ref={parentRef}
      className="tc-animation-lab__canvas"
      aria-label="TitanCore animation production proof"
    />
  );
}
