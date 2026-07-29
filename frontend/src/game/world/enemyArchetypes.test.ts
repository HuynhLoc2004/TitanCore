import { describe, expect, it } from 'vitest';
import { ENEMY_ARCHETYPES } from './enemyArchetypes';

describe('enemy archetype bounds', () => {
  it('keeps the first hunt profiles readable and mobile-safe', () => {
    expect(Object.keys(ENEMY_ARCHETYPES)).toHaveLength(3);
    Object.values(ENEMY_ARCHETYPES).forEach((profile) => {
      expect(profile.maxHp).toBeGreaterThanOrEqual(50);
      expect(profile.maxHp).toBeLessThanOrEqual(200);
      expect(profile.damage).toBeGreaterThan(0);
      expect(profile.damage).toBeLessThanOrEqual(20);
      expect(profile.behavior.telegraphMs).toBeGreaterThanOrEqual(500);
      expect(profile.behavior.chaseSpeed).toBeLessThanOrEqual(120);
    });
  });

  it('gives scout, bruiser and warden distinct combat jobs', () => {
    expect(ENEMY_ARCHETYPES.SPROUT_SCOUT.behavior.chaseSpeed)
      .toBeGreaterThan(ENEMY_ARCHETYPES.SPROUT_BRUISER.behavior.chaseSpeed);
    expect(ENEMY_ARCHETYPES.SPROUT_BRUISER.maxHp)
      .toBeGreaterThan(ENEMY_ARCHETYPES.SPROUT_SCOUT.maxHp);
    expect(ENEMY_ARCHETYPES.SPROUT_WARDEN.behavior.attackRange)
      .toBeGreaterThan(ENEMY_ARCHETYPES.SPROUT_BRUISER.behavior.attackRange);
  });
});
