import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useRef } from 'react';
import { useLobbyParallax, useLobbyQuality } from './useLobbyQuality';

type MediaController = {
  matches: boolean;
  listeners: Set<() => void>;
};

const media = new Map<string, MediaController>();

function installMatchMedia(values: Record<string, boolean>) {
  media.clear();
  Object.entries(values).forEach(([query, matches]) => {
    media.set(query, { matches, listeners: new Set() });
  });
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn((query: string) => {
      const controller = media.get(query) ?? { matches: false, listeners: new Set() };
      media.set(query, controller);
      return {
        media: query,
        get matches() {
          return controller.matches;
        },
        addEventListener: (_: string, listener: () => void) => controller.listeners.add(listener),
        removeEventListener: (_: string, listener: () => void) => controller.listeners.delete(listener),
      };
    }),
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});
describe('lobby motion quality', () => {
  it('honors reduced motion before any automatic LOW signal', () => {
    installMatchMedia({
      '(prefers-reduced-motion: reduce)': true,
      '(max-width: 680px)': true,
    });
    const { result, unmount } = renderHook(() => useLobbyQuality());
    expect(result.current).toBe('REDUCED');
    unmount();
    expect([...media.values()].every((entry) => entry.listeners.size === 0)).toBe(true);
  });

  it('uses LOW on a compact viewport and STANDARD otherwise', () => {
    installMatchMedia({
      '(prefers-reduced-motion: reduce)': false,
      '(max-width: 680px)': true,
    });
    const compact = renderHook(() => useLobbyQuality());
    expect(compact.result.current).toBe('LOW');
    compact.unmount();

    installMatchMedia({
      '(prefers-reduced-motion: reduce)': false,
      '(max-width: 680px)': false,
    });
    const standard = renderHook(() => useLobbyQuality());
    expect(standard.result.current).toBe('STANDARD');
    standard.unmount();
  });

  it('removes parallax listeners and pending animation frames on unmount', () => {
    const element = document.createElement('main');
    document.body.append(element);
    const requestFrame = vi.spyOn(window, 'requestAnimationFrame')
      .mockImplementation(() => 17);
    const cancelFrame = vi.spyOn(window, 'cancelAnimationFrame');
    const removeWindow = vi.spyOn(window, 'removeEventListener');
    const removeDocument = vi.spyOn(document, 'removeEventListener');

    const { unmount } = renderHook(() => {
      const ref = useRef<HTMLElement | null>(element);
      useLobbyParallax(ref, true);
    });
    act(() => window.dispatchEvent(new PointerEvent('pointermove', {
      clientX: 400,
      clientY: 300,
    })));
    expect(requestFrame).toHaveBeenCalled();

    unmount();
    expect(cancelFrame).toHaveBeenCalledWith(17);
    expect(removeWindow).toHaveBeenCalledWith('pointermove', expect.any(Function));
    expect(removeDocument).toHaveBeenCalledWith('visibilitychange', expect.any(Function));
    element.remove();
  });
});
