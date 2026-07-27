import { useEffect, useState, type RefObject } from 'react';
import { useReducedMotion } from './useMotionPreferences';

export type LobbyQuality = 'STANDARD' | 'LOW' | 'REDUCED';

type NetworkInformation = {
  saveData?: boolean;
};

export function useLobbyQuality(): LobbyQuality {
  const reduced = useReducedMotion();
  const [lowViewport, setLowViewport] = useState(
    () => window.matchMedia?.('(max-width: 680px)').matches ?? false,
  );

  useEffect(() => {
    if (!window.matchMedia) {
      return;
    }
    const media = window.matchMedia('(max-width: 680px)');
    const update = () => setLowViewport(media.matches);
    update();
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);

  if (reduced) {
    return 'REDUCED';
  }
  const connection = (navigator as Navigator & { connection?: NetworkInformation }).connection;
  return lowViewport || connection?.saveData === true ? 'LOW' : 'STANDARD';
}

export function useLobbyParallax(
  container: RefObject<HTMLElement | null>,
  enabled: boolean,
) {
  useEffect(() => {
    const element = container.current;
    if (!element || !enabled) {
      return;
    }
    let frame = 0;
    let x = 0;
    let y = 0;
    const render = () => {
      frame = 0;
      element.style.setProperty('--tc-lobby-shift-x', `${x}px`);
      element.style.setProperty('--tc-lobby-shift-y', `${y}px`);
    };
    const pointer = (event: PointerEvent) => {
      if (document.visibilityState !== 'visible') {
        return;
      }
      x = ((event.clientX / Math.max(window.innerWidth, 1)) - 0.5) * 12;
      y = ((event.clientY / Math.max(window.innerHeight, 1)) - 0.5) * 8;
      if (frame === 0) {
        frame = window.requestAnimationFrame(render);
      }
    };
    const reset = () => {
      if (document.visibilityState !== 'visible') {
        x = 0;
        y = 0;
        if (frame === 0) {
          frame = window.requestAnimationFrame(render);
        }
      }
    };
    window.addEventListener('pointermove', pointer, { passive: true });
    document.addEventListener('visibilitychange', reset);
    return () => {
      window.removeEventListener('pointermove', pointer);
      document.removeEventListener('visibilitychange', reset);
      if (frame !== 0) {
        window.cancelAnimationFrame(frame);
      }
      element.style.removeProperty('--tc-lobby-shift-x');
      element.style.removeProperty('--tc-lobby-shift-y');
    };
  }, [container, enabled]);
}
