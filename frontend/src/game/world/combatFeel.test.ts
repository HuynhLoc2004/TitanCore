import { describe, expect, it } from 'vitest';
import {
  COMBAT_FEEL_SKILLS,
  isSkillReady,
  newlyPressedActions,
  selectTargets,
} from './combatFeel';

describe('local combat feel contract', () => {
  it('uses bounded cooldown, range and damage values', () => {
    COMBAT_FEEL_SKILLS.forEach((skill) => {
      expect(skill.cooldownMs).toBeGreaterThanOrEqual(400);
      expect(skill.cooldownMs).toBeLessThanOrEqual(6000);
      expect(skill.range).toBeGreaterThanOrEqual(180);
      expect(skill.range).toBeLessThanOrEqual(400);
      expect(skill.damage).toBeGreaterThan(0);
      expect(skill.damage).toBeLessThanOrEqual(50);
    });
  });

  it('fires only on an input edge and respects cooldown readiness', () => {
    expect(newlyPressedActions(new Set(['ATTACK']), new Set()).has('ATTACK')).toBe(true);
    expect(newlyPressedActions(new Set(['ATTACK']), new Set(['ATTACK'])).size).toBe(0);
    expect(isSkillReady('ATTACK', 999, new Map([['ATTACK', 1000]]))).toBe(false);
    expect(isSkillReady('ATTACK', 1000, new Map([['ATTACK', 1000]]))).toBe(true);
  });

  it('selects a deterministic nearest target or all in-range area targets', () => {
    const targets = [
      { id: 'far', x: 180, y: 0, active: true },
      { id: 'near-b', x: 80, y: 0, active: true },
      { id: 'near-a', x: -80, y: 0, active: true },
      { id: 'down', x: 10, y: 0, active: false },
    ];
    expect(selectTargets({ x: 0, y: 0 }, targets, 200, false)[0].id).toBe('near-a');
    expect(selectTargets({ x: 0, y: 0 }, targets, 100, true).map(({ id }) => id))
      .toEqual(['near-a', 'near-b']);
  });

  it('keeps directed attacks in front while area skills remain omnidirectional', () => {
    const targets = [
      { id: 'behind', x: -20, y: 0, active: true },
      { id: 'ahead', x: 80, y: 0, active: true },
    ];
    expect(selectTargets({ x: 0, y: 0 }, targets, 100, false, 1)[0].id).toBe('ahead');
    expect(selectTargets({ x: 0, y: 0 }, targets, 100, true, 1)).toHaveLength(2);
  });
});
