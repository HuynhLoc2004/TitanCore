import { act, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WorldRuntimeHost } from './WorldRuntimeHost';
import { UnifiedInputState } from './world/input/UnifiedInputState';

const destroy = vi.fn();
const setReducedMotion = vi.fn();
const create = vi.fn();
const loadRenderer = vi.fn(async () => create);
const inputState = new UnifiedInputState();

describe('WorldRuntimeHost', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('survives Strict Mode and destroys each created runtime', async () => {
    create.mockImplementation(({ onReady }: { onReady: () => void }) => {
      onReady();
      return { destroy, setReducedMotion };
    });
    const view = render(
      <StrictMode>
        <WorldRuntimeHost
          manifestUrl="/assets/test/world.json"
          reducedMotion={false}
          inputState={inputState}
          retryGeneration={0}
          onStatus={vi.fn()}
          onMetrics={vi.fn()}
          loadRenderer={loadRenderer}
        />
      </StrictMode>,
    );

    await waitFor(() => expect(create).toHaveBeenCalled());
    view.unmount();

    expect(destroy).toHaveBeenCalledTimes(create.mock.calls.length);
  });

  it('fails safely after a bounded startup timeout', async () => {
    vi.useFakeTimers();
    const onStatus = vi.fn();
    create.mockReturnValue({ destroy, setReducedMotion });
    render(
      <WorldRuntimeHost
        manifestUrl="/assets/test/world.json"
        reducedMotion={false}
        inputState={inputState}
        retryGeneration={0}
        onStatus={onStatus}
        onMetrics={vi.fn()}
        loadRenderer={loadRenderer}
      />,
    );
    await act(async () => {
      await Promise.resolve();
    });
    act(() => {
      vi.advanceTimersByTime(20_000);
    });

    expect(screen.getByRole('alert')).toHaveTextContent(/could not be prepared/i);
    expect(onStatus).toHaveBeenCalledWith(expect.objectContaining({ phase: 'FAILED' }));
    expect(destroy).toHaveBeenCalledOnce();
  });
});
