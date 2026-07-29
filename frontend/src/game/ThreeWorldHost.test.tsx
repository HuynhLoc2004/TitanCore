import { render, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ThreeWorldHost } from './ThreeWorldHost';

const destroy = vi.fn();
const setReducedMotion = vi.fn();
const createRuntime = vi.fn(() => ({ destroy, setReducedMotion }));
const loadRenderer = vi.fn(async () => createRuntime);

describe('ThreeWorldHost', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('lazy-loads and disposes every Three.js runtime under Strict Mode', async () => {
    const view = render(
      <StrictMode>
        <ThreeWorldHost
          reducedMotion={false}
          onStatus={vi.fn()}
          onMetrics={vi.fn()}
          loadRenderer={loadRenderer}
        />
      </StrictMode>,
    );

    await waitFor(() => expect(createRuntime).toHaveBeenCalled());
    view.unmount();

    expect(destroy).toHaveBeenCalledTimes(createRuntime.mock.calls.length);
  });

  it('forwards reduced-motion updates without recreating the runtime', async () => {
    const view = render(
      <ThreeWorldHost
        reducedMotion={false}
        onStatus={vi.fn()}
        onMetrics={vi.fn()}
        loadRenderer={loadRenderer}
      />,
    );
    await waitFor(() => expect(createRuntime).toHaveBeenCalledOnce());

    view.rerender(
      <ThreeWorldHost
        reducedMotion
        onStatus={vi.fn()}
        onMetrics={vi.fn()}
        loadRenderer={loadRenderer}
      />,
    );

    expect(createRuntime).toHaveBeenCalledOnce();
    expect(setReducedMotion).toHaveBeenLastCalledWith(true);
  });
});
