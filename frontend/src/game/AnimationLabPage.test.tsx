import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AnimationLabPage } from './AnimationLabPage';

const navigateMock = vi.fn();

vi.mock('../app/AppRoutes', () => ({
  navigate: (route: string) => navigateMock(route),
}));

vi.mock('./GameHost', () => ({
  GameHost: ({
    reducedMotion,
    onMetrics,
  }: {
    reducedMotion: boolean;
    onMetrics: (value: {
      fps: number;
      frameTimeMs: number;
      renderedObjects: number;
      renderer: 'WEBGL';
    }) => void;
  }) => (
    <button
      type="button"
      data-motion={String(reducedMotion)}
      onClick={() => onMetrics({
        fps: 60,
        frameTimeMs: 16.7,
        renderedObjects: 52,
        renderer: 'WEBGL',
      })}
    >
      proof host
    </button>
  ),
}));

describe('AnimationLabPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: false,
      media: '(prefers-reduced-motion: reduce)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));
  });

  it('shows proof scope and bounded performance metrics', () => {
    render(<AnimationLabPage />);

    fireEvent.click(screen.getByRole('button', { name: 'proof host' }));

    expect(screen.getByRole('heading', { name: /motion forge/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/animation performance metrics/i)).toHaveTextContent('60 FPS');
    expect(screen.getByText(/combat timing, damage, hitboxes/i)).toBeInTheDocument();
  });

  it('applies reduced motion and returns to camp through the safe route', () => {
    render(<AnimationLabPage />);

    const toggle = screen.getByRole('checkbox', { name: /reduce motion/i });
    fireEvent.click(toggle);
    expect(screen.getByRole('button', { name: 'proof host' })).toHaveAttribute('data-motion', 'true');

    fireEvent.click(screen.getByRole('button', { name: /return to camp/i }));
    expect(navigateMock).toHaveBeenCalledWith('/app');
  });
});
