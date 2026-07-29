import { describe, expect, it } from 'vitest';
import { advanceEnemyBehavior, SPROUT_BEHAVIOR, type EnemyBehavior } from './enemyBehavior';

const patrol = (): EnemyBehavior => ({ state: 'PATROL', stateUntil: 0, patrolDirection: 1 });

describe('enemy behavior state machine', () => {
  it('aggros, telegraphs and attacks only after the readable warning window', () => {
    const chase = advanceEnemyBehavior(patrol(), 100, 200, 0).behavior;
    expect(chase.state).toBe('CHASE');
    const warning = advanceEnemyBehavior(chase, 200, 80, 20).behavior;
    expect(warning.state).toBe('TELEGRAPH');
    expect(advanceEnemyBehavior(warning, 200 + SPROUT_BEHAVIOR.telegraphMs - 1, 80, 20).attack)
      .toBe(false);
    const impact = advanceEnemyBehavior(
      warning,
      200 + SPROUT_BEHAVIOR.telegraphMs,
      80,
      20,
    );
    expect(impact.attack).toBe(true);
    expect(impact.behavior.state).toBe('RECOVER');
  });

  it('returns to patrol outside its leash and reverses at patrol bounds', () => {
    const leashed = advanceEnemyBehavior(
      { ...patrol(), state: 'CHASE' },
      0,
      300,
      SPROUT_BEHAVIOR.leashRange + 1,
    );
    expect(leashed.behavior.state).toBe('PATROL');
    expect(advanceEnemyBehavior(patrol(), 0, 900, SPROUT_BEHAVIOR.patrolRadius).behavior
      .patrolDirection).toBe(-1);
  });

  it('keeps defeated enemies inert', () => {
    const result = advanceEnemyBehavior(
      { ...patrol(), state: 'DEFEATED' },
      10_000,
      1,
      0,
    );
    expect(result.attack).toBe(false);
    expect(result.behavior.state).toBe('DEFEATED');
  });
});
