import { describe, expect, it } from 'vitest';
import { UnifiedInputState } from './UnifiedInputState';

describe('UnifiedInputState', () => {
  it('normalizes diagonal movement and combines independent sources', () => {
    const input = new UnifiedInputState();
    input.setMove('KEYBOARD', 1, 1);
    input.setMove('TOUCH', 1, 0);

    const snapshot = input.snapshot();
    expect(Math.hypot(snapshot.moveX, snapshot.moveY)).toBeCloseTo(1, 3);
    expect(snapshot.moveX).toBeGreaterThan(snapshot.moveY);
  });

  it('releases all state for only the requested source', () => {
    const input = new UnifiedInputState();
    input.setMove('TOUCH', 1, 0);
    input.setAim('TOUCH', 0, 1);
    input.setAction('TOUCH', 'ATTACK', true);
    input.setMove('KEYBOARD', -1, 0);

    input.releaseSource('TOUCH');

    expect(input.snapshot()).toMatchObject({ moveX: -1, moveY: 0, aimX: 0, aimY: 0 });
    expect(input.snapshot().actions.size).toBe(0);
  });

  it('clears stale input when a runtime generation starts', () => {
    const input = new UnifiedInputState();
    input.setAction('KEYBOARD', 'DODGE', true);

    expect(input.beginGeneration()).toBe(1);
    expect(input.snapshot().actions.size).toBe(0);
  });

  it('does not advance sequence for repeated neutral input', () => {
    const input = new UnifiedInputState();
    input.setMove('KEYBOARD', 0, 0);
    input.setMove('KEYBOARD', 0, 0);

    expect(input.snapshot().sequence).toBe(0);
  });
});
