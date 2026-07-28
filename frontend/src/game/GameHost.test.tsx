import { act, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GameHost } from './GameHost';

const destroyMock = vi.fn();
const setReducedMotionMock = vi.fn();
const createAnimationProofMock = vi.fn();
const loadRendererMock = vi.fn(async () => createAnimationProofMock);

describe('GameHost', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('survives the Strict Mode lifecycle and destroys every created game', async () => {
    createAnimationProofMock.mockImplementation(({ onReady }: { onReady: () => void }) => {
      onReady();
      return {
        destroy: destroyMock,
        setReducedMotion: setReducedMotionMock,
      };
    });

    const view = render(
      <StrictMode>
        <GameHost
          reducedMotion={false}
          onMetrics={vi.fn()}
          loadRenderer={loadRendererMock}
        />
      </StrictMode>,
    );

    await waitFor(() => expect(createAnimationProofMock).toHaveBeenCalled());

    view.unmount();
    expect(destroyMock).toHaveBeenCalledTimes(createAnimationProofMock.mock.calls.length);
  });

  it('shows a bounded safe error when the renderer never becomes ready', async () => {
    vi.useFakeTimers();
    createAnimationProofMock.mockReturnValue({
      destroy: destroyMock,
      setReducedMotion: setReducedMotionMock,
    });

    render(
      <GameHost
        reducedMotion={false}
        onMetrics={vi.fn()}
        loadRenderer={loadRendererMock}
      />,
    );
    await act(async () => {
      await Promise.resolve();
    });
    act(() => {
      vi.advanceTimersByTime(15_000);
    });

    expect(screen.getByRole('alert')).toHaveTextContent(/could not start/i);
    expect(destroyMock).toHaveBeenCalledOnce();
  });
});
