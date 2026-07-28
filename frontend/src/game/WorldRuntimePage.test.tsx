import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorldRuntimePage } from './WorldRuntimePage';

const navigate = vi.fn();

vi.mock('../app/AppRoutes', () => ({
  navigate: (route: string) => navigate(route),
}));
vi.mock('./WorldRuntimeHost', () => ({
  WorldRuntimeHost: ({
    reducedMotion,
    onStatus,
    onMetrics,
  }: {
    reducedMotion: boolean;
    onStatus: (status: {
      phase: 'READY';
      progress: number;
      message: string;
    }) => void;
    onMetrics: (metrics: {
      fps: number;
      frameTimeMs: number;
      simulationHz: number;
      renderedObjects: number;
      worldScreens: number;
      regionCount: number;
      droppedSimulationMs: number;
      renderer: 'WEBGL';
      quality: 'HIGH';
    }) => void;
  }) => (
    <button
      type="button"
      data-motion={String(reducedMotion)}
      onClick={() => {
        onStatus({ phase: 'READY', progress: 100, message: 'Local Khu runtime ready' });
        onMetrics({
          fps: 60,
          frameTimeMs: 16.7,
          simulationHz: 30,
          renderedObjects: 40,
          worldScreens: 4,
          regionCount: 5,
          droppedSimulationMs: 0,
          renderer: 'WEBGL',
          quality: 'HIGH',
        });
      }}
    >
      runtime host
    </button>
  ),
}));

describe('WorldRuntimePage', () => {
  beforeEach(() => {
    navigate.mockReset();
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({ matches: false }));
  });

  it('reports fixed simulation and renderer metrics without per-frame React work', () => {
    render(<WorldRuntimePage />);
    fireEvent.click(screen.getByRole('button', { name: 'runtime host' }));

    const metrics = screen.getByLabelText(/world runtime metrics/i);
    expect(metrics).toHaveTextContent('60 FPS');
    expect(metrics).toHaveTextContent('30 Hz sim');
    expect(metrics).toHaveTextContent('4 screens');
    expect(metrics).toHaveTextContent('5 regions');
    expect(metrics).toHaveTextContent('HIGH');
    expect(screen.getByText(/local collision and elevation proof/i)).toBeInTheDocument();
    expect(screen.getByText(/combat, loot and multiplayer authority remain outside/i))
      .toBeInTheDocument();
  });

  it('forwards reduced motion and returns through the safe app route', () => {
    render(<WorldRuntimePage />);
    fireEvent.click(screen.getByRole('checkbox', { name: /reduce motion/i }));
    expect(screen.getByRole('button', { name: 'runtime host' }))
      .toHaveAttribute('data-motion', 'true');

    fireEvent.click(screen.getByRole('button', { name: /return to camp/i }));
    expect(navigate).toHaveBeenCalledWith('/app');
  });
});
