import { describe, expect, it } from 'vitest';
import {
  animationKeyFor,
  CORE_RAIDER_ANIMATIONS,
  CORE_RAIDER_FRAME,
  selectLocomotionState,
} from './locomotion';

describe('Core Raider locomotion contract', () => {
  it('uses a stable mobile-safe frame and ground pivot', () => {
    expect(CORE_RAIDER_FRAME).toEqual({
      width: 444,
      height: 444,
      pivotX: 222,
      groundY: 412,
    });
  });

  it('selects idle, analog walk and full-speed run deterministically', () => {
    expect(selectLocomotionState(0, 0)).toBe('IDLE');
    expect(selectLocomotionState(0.4, 0.2)).toBe('WALK');
    expect(selectLocomotionState(1, 0)).toBe('RUN');
    expect(animationKeyFor('WALK')).toBe(CORE_RAIDER_ANIMATIONS.walk.key);
  });

  it('keeps dodge visual-only and non-looping', () => {
    expect(CORE_RAIDER_ANIMATIONS.dodgeVisual).toMatchObject({
      frames: [6, 7, 6],
      repeat: 0,
    });
  });
});
