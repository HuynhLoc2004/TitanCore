import { describe, expect, it, vi } from 'vitest';
import { FixedStepClock } from './fixedStepClock';

describe('FixedStepClock', () => {
  it('runs simulation at a stable step independent from render deltas', () => {
    const clock = new FixedStepClock(10, 5, 100);
    const step = vi.fn();

    expect(clock.advance(6, step)).toEqual({ alpha: 0.6, steps: 0, droppedMs: 0 });
    expect(clock.advance(9, step)).toEqual({ alpha: 0.5, steps: 1, droppedMs: 0 });
    expect(step).toHaveBeenCalledExactlyOnceWith(10);
  });

  it('bounds catch-up work and reports dropped simulation time', () => {
    const clock = new FixedStepClock(10, 3, 100);
    const step = vi.fn();

    const result = clock.advance(250, step);

    expect(result.steps).toBe(3);
    expect(result.droppedMs).toBe(220);
    expect(result.alpha).toBe(0);
    expect(step).toHaveBeenCalledTimes(3);
  });

  it('clears stale accumulated time when reset', () => {
    const clock = new FixedStepClock(10, 3, 100);
    const step = vi.fn();
    clock.advance(9, step);
    clock.reset();

    expect(clock.advance(1, step).steps).toBe(0);
  });
});
